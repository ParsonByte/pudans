package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiLangChain4jMapperTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final OpenAiLangChain4jMapper subject = new OpenAiLangChain4jMapper(mapper);

    @Test
    void mapsMessagesParametersAndTools() {
        var request = subject.toRequest(chatIr(), "gpt-4.1", 2048);

        assertThat(request.messages()).hasSize(4);
        assertThat(request.messages().get(0))
                .isInstanceOfSatisfying(SystemMessage.class, message ->
                        assertThat(message.text()).isEqualTo("You are helpful."));
        assertThat(request.messages().get(1))
                .isInstanceOfSatisfying(UserMessage.class, message ->
                        assertThat(message.singleText()).isEqualTo("hello"));
        assertThat(request.messages().get(2))
                .isInstanceOfSatisfying(AiMessage.class, message -> {
                    assertThat(message.text()).isEqualTo("checking");
                    assertThat(message.thinking()).isEqualTo("Let me think");
                    assertThat(message.toolExecutionRequests()).singleElement()
                            .isInstanceOfSatisfying(ToolExecutionRequest.class, call -> {
                                assertThat(call.id()).isEqualTo("call_1");
                                assertThat(call.name()).isEqualTo("get_weather");
                                assertThat(call.arguments()).isEqualTo("{\"city\":\"Paris\"}");
                            });
                });
        assertThat(request.messages().get(3))
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class, message -> {
                    assertThat(message.id()).isEqualTo("call_1");
                    assertThat(message.toolName()).isEqualTo("get_weather");
                    assertThat(message.text()).isEqualTo("{\"temperature\":20}");
                });

        var parameters = (OpenAiChatRequestParameters) request.parameters();
        assertThat(parameters.modelName()).isEqualTo("gpt-4.1");
        assertThat(parameters.temperature()).isEqualTo(0.2);
        assertThat(parameters.topP()).isEqualTo(0.9);
        assertThat(parameters.maxOutputTokens()).isEqualTo(2048);
        assertThat(parameters.stopSequences()).containsExactly("STOP");
        assertThat(parameters.toolSpecifications()).singleElement()
                .satisfies(tool -> assertThat(tool.name()).isEqualTo("get_weather"));
        assertThat(parameters.toolChoice()).isEqualTo(dev.langchain4j.model.chat.request.ToolChoice.AUTO);
        assertThat(parameters.parallelToolCalls()).isTrue();
        assertThat(parameters.seed()).isEqualTo(7);
        assertThat(parameters.metadata()).containsEntry("trace", "abc");
        assertThat(parameters.customParameters())
                .containsEntry("enable_thinking", Boolean.TRUE);
    }

    @Test
    void mapsFilesByMediaType() {
        var request = new AiChatIr(
                List.of(new AiChatMessage(
                        "user",
                        List.of(
                                new AiContentPart.File(
                                        "image/png", "aW1n", null, "a.png", null, null),
                                new AiContentPart.File(
                                        "video/mp4", "dmlk", null, "a.mp4", null, null),
                                new AiContentPart.File(
                                        "audio/mpeg", "YXVk", null, "a.mp3", null, null),
                                new AiContentPart.File(
                                        "application/pdf", "cGRm", null, "a.pdf", null, null)))),
                List.of(),
                new AiRequestParams("gpt-4.1", null, null, null, null, List.of(), null, Map.of()),
                false);

        var message = (UserMessage) subject
                .toRequest(request, "gpt-4.1", null)
                .messages()
                .getFirst();

        assertThat(message.contents()).hasSize(4);
        assertThat(message.contents().get(0)).isInstanceOf(ImageContent.class);
        assertThat(message.contents().get(1)).isInstanceOf(VideoContent.class);
        assertThat(message.contents().get(2)).isInstanceOf(AudioContent.class);
        assertThat(message.contents().get(3)).isInstanceOf(PdfFileContent.class);
    }

    @Test
    void mapsSpecificToolChoiceToCustomParameters() {
        var choice = mapper.createObjectNode().put("type", "function");
        choice.putObject("function").put("name", "get_weather");
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(
                        "gpt-4.1", null, null, null, null, List.of(), choice, Map.of()),
                false);

        var parameters = (OpenAiChatRequestParameters) subject
                .toRequest(request, "gpt-4.1", null)
                .parameters();

        assertThat(parameters.toolChoice()).isNull();
        assertThat(parameters.customParameters()).containsKey("tool_choice");
        assertThat(parameters.customParameters().get("tool_choice"))
                .isInstanceOfSatisfying(Map.class, toolChoice ->
                        assertThat(toolChoice.get("function")).isInstanceOfSatisfying(
                                Map.class,
                                function -> assertThat(function.get("name"))
                                        .isEqualTo("get_weather")));
    }

    @Test
    void mapsResponseUsageAndFinishReason() {
        var usage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(4)
                        .cacheWriteTokens(2)
                        .build())
                .build();
        var response = ChatResponse.builder()
                .id("chatcmpl-1")
                .modelName("gpt-4.1")
                .aiMessage(AiMessage.builder()
                        .thinking("Let me think")
                        .text("Hello")
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("call_1")
                                .name("get_weather")
                                .arguments("{\"city\":\"Paris\"}")
                                .build()))
                        .build())
                .tokenUsage(usage)
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();

        var mapped = subject.toResponse(response);

        assertThat(mapped.id()).isEqualTo("chatcmpl-1");
        assertThat(mapped.model()).isEqualTo("gpt-4.1");
        assertThat(mapped.finishReason()).isEqualTo("tool_calls");
        assertThat(mapped.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, 2));
        assertThat(mapped.message().parts())
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                                assertThat(thinking.text()).isEqualTo("Let me think")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call -> {
                            assertThat(call.id()).isEqualTo("call_1");
                            assertThat(call.name()).isEqualTo("get_weather");
                        }));
    }

    @Test
    void mapsStreamingEvents() {
        var text = subject.toStreamEvent(new PartialResponse("Hel")).orElseThrow();
        assertThat(text.content()).isEqualTo("Hel");

        var thinking = subject.toStreamEvent(new PartialThinking("hmm")).orElseThrow();
        assertThat(thinking.reasoning()).isEqualTo("hmm");

        var partial = subject.toStreamEvent(PartialToolCall.builder()
                        .index(0)
                        .id("call_1")
                        .name("get_weather")
                        .partialArguments("{\"city\"")
                        .build())
                .orElseThrow()
                .toolCall();
        assertThat(partial.arguments()).isEqualTo("{\"city\"");

        var complete = ChatResponse.builder()
                .id("chatcmpl-1")
                .modelName("gpt-4.1")
                .aiMessage(AiMessage.from("Hello"))
                .tokenUsage(OpenAiTokenUsage.builder()
                        .inputTokenCount(10)
                        .outputTokenCount(20)
                        .build())
                .finishReason(FinishReason.STOP)
                .build();
        var done = subject.toStreamEvent(new CompleteResponse(complete)).orElseThrow();
        assertThat(done.finishReason()).isEqualTo("stop");
        assertThat(done.usage()).isEqualTo(new AiTokenUsage(10, 20, null, null));
    }

    private AiChatIr chatIr() {
        var parameters = mapper.createObjectNode().put("type", "object");
        parameters.putObject("properties").putObject("city").put("type", "string");
        var arguments = mapper.createObjectNode().put("city", "Paris");
        var toolResult = mapper.createObjectNode().put("temperature", 20);
        var metadata = mapper.createObjectNode().put("trace", "abc");
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null))),
                        new AiChatMessage("assistant", List.of(
                                new AiContentPart.Thinking("Let me think", null, null),
                                new AiContentPart.Text("checking", null),
                                new AiContentPart.ToolCall("call_1", "get_weather", arguments, null))),
                        new AiChatMessage(
                                "user", List.of(new AiContentPart.ToolResult("call_1", toolResult, null, null)))),
                List.of(new AiTool("get_weather", "Weather lookup", parameters, null)),
                new AiRequestParams(
                        "gpt-4.1",
                        0.2,
                        2048,
                        0.9,
                        null,
                        List.of("STOP"),
                        mapper.getNodeFactory().textNode("auto"),
                        Map.of(
                                "parallel_tool_calls", mapper.getNodeFactory().booleanNode(true),
                                "seed", mapper.getNodeFactory().numberNode(7),
                                "metadata", metadata,
                                "enable_thinking", mapper.getNodeFactory().booleanNode(true))),
                false);
    }
}