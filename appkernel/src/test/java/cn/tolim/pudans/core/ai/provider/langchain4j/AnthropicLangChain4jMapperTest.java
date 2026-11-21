package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicLangChain4jMapperTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AnthropicLangChain4jMapper subject = new AnthropicLangChain4jMapper(mapper);

    @Test
    void mapsMessagesParametersAndTools() {
        var request = subject.toRequest(chatIr(), "claude-sonnet-4-5", 2048);

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
                    assertThat(message.attribute("thinking_signature", String.class)).isEqualTo("sig-1");
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

        var parameters = (AnthropicChatRequestParameters) request.parameters();
        assertThat(parameters.modelName()).isEqualTo("claude-sonnet-4-5");
        assertThat(parameters.temperature()).isEqualTo(0.2);
        assertThat(parameters.topP()).isEqualTo(0.9);
        assertThat(parameters.topK()).isEqualTo(5);
        assertThat(parameters.maxOutputTokens()).isEqualTo(2048);
        assertThat(parameters.stopSequences()).containsExactly("STOP");
        assertThat(parameters.toolSpecifications()).singleElement()
                .satisfies(tool -> assertThat(tool.name()).isEqualTo("get_weather"));
        assertThat(parameters.toolChoiceName()).isEqualTo("get_weather");
        assertThat(parameters.thinkingType()).isEqualTo("enabled");
        assertThat(parameters.thinkingBudgetTokens()).isEqualTo(1024);
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        var request = new AiChatIr(
                List.of(new AiChatMessage(
                        "user",
                        List.of(new AiContentPart.File("text/plain", "aGVsbG8=", null, "a.txt", null, null)))),
                List.of(),
                new AiRequestParams("claude-sonnet-4-5", null, null, null, null, List.of(), null, Map.of()),
                false);

        assertThatThrownBy(() -> subject.toRequest(request, "claude-sonnet-4-5", null))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void mapsResponsePartsUsageAndFinishReason() {
        var response = ChatResponse.builder()
                .id("msg_1")
                .modelName("claude-sonnet-4-5")
                .aiMessage(AiMessage.builder()
                        .thinking("Let me think")
                        .text("Hello")
                        .attributes(Map.of("thinking_signature", "sig-1"))
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("toolu_1")
                                .name("get_weather")
                                .arguments("{\"city\":\"Paris\"}")
                                .build()))
                        .build())
                .tokenUsage(AnthropicTokenUsage.builder()
                        .inputTokenCount(10)
                        .outputTokenCount(20)
                        .cacheReadInputTokens(4)
                        .cacheCreationInputTokens(2)
                        .build())
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();

        var mapped = subject.toResponse(response);

        assertThat(mapped.id()).isEqualTo("msg_1");
        assertThat(mapped.model()).isEqualTo("claude-sonnet-4-5");
        assertThat(mapped.finishReason()).isEqualTo("tool_calls");
        assertThat(mapped.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, 2));
        assertThat(mapped.message().role()).isEqualTo("assistant");
        assertThat(mapped.message().parts())
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking -> {
                            assertThat(thinking.text()).isEqualTo("Let me think");
                            assertThat(thinking.signature()).isEqualTo("sig-1");
                        }))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call -> {
                            assertThat(call.id()).isEqualTo("toolu_1");
                            assertThat(call.name()).isEqualTo("get_weather");
                            assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                        }));
    }

    @Test
    void mapsStreamingEvents() {
        var text = subject.toStreamEvent(new PartialResponse("Hel")).orElseThrow();
        assertThat(text.content()).isEqualTo("Hel");

        var thinking = subject.toStreamEvent(new PartialThinking("hmm")).orElseThrow();
        assertThat(thinking.reasoning()).isEqualTo("hmm");

        var partial = subject.toStreamEvent(PartialToolCall.builder()
                        .index(1)
                        .id("toolu_1")
                        .name("get_weather")
                        .partialArguments("{\"city\"")
                        .build())
                .orElseThrow()
                .toolCall();
        assertThat(partial.index()).isEqualTo(1);
        assertThat(partial.id()).isEqualTo("toolu_1");
        assertThat(partial.name()).isEqualTo("get_weather");
        assertThat(partial.arguments()).isEqualTo("{\"city\"");

        var complete = ChatResponse.builder()
                .id("msg_1")
                .modelName("claude-sonnet-4-5")
                .aiMessage(AiMessage.from("Hello"))
                .tokenUsage(new AnthropicTokenUsage.Builder()
                        .inputTokenCount(10)
                        .outputTokenCount(20)
                        .build())
                .finishReason(FinishReason.STOP)
                .build();
        var done = subject.toStreamEvent(new CompleteResponse(complete)).orElseThrow();
        assertThat(done.finishReason()).isEqualTo("stop");
        assertThat(done.usage()).isEqualTo(new AiTokenUsage(10, 20, null, null));

        assertThat(subject.toStreamEvent(RawStreamingEvent.of("raw"))).isEmpty();
    }

    private AiChatIr chatIr() {
        var parameters = mapper.createObjectNode().put("type", "object");
        parameters.putObject("properties").putObject("city").put("type", "string");
        var arguments = mapper.createObjectNode().put("city", "Paris");
        var toolResult = mapper.createObjectNode().put("temperature", 20);
        var toolChoice = mapper.createObjectNode().put("type", "tool").put("name", "get_weather");
        var thinking = mapper.createObjectNode()
                .put("type", "enabled")
                .put("budget_tokens", 1024);
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null))),
                        new AiChatMessage("assistant", List.of(
                                new AiContentPart.Thinking("Let me think", "sig-1", null),
                                new AiContentPart.Text("checking", null),
                                new AiContentPart.ToolCall("call_1", "get_weather", arguments, null))),
                        new AiChatMessage(
                                "user", List.of(new AiContentPart.ToolResult("call_1", toolResult, null, null)))),
                List.of(new AiTool("get_weather", "Weather lookup", parameters, null)),
                new AiRequestParams(
                        "claude-sonnet-4-5",
                        0.2,
                        2048,
                        0.9,
                        5,
                        List.of("STOP"),
                        toolChoice,
                        Map.of("thinking", thinking)),
                false);
    }

    @Test
    void keepsResolvedModelAndTokenDefaults() {
        var model = new AiModel();
        model.getSpec().setModelCode("claude-model");
        model.getSpec().setMaxOutputTokens(4096);
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(null, null, null, null, null, List.of(), null, Map.of()),
                false);

        assertThat(subject.modelCode(model, request)).isEqualTo("claude-model");
        assertThat(subject.maxTokens(model, request)).isEqualTo(4096);
    }

    @Test
    void requiresAModelCode() {
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(null, null, null, null, null, List.of(), null, Map.of()),
                false);

        assertThatThrownBy(() -> subject.modelCode(null, request))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("模型编码");
    }
}