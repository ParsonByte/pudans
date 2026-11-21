package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiResponsesLangChain4jMapperTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final OpenAiResponsesLangChain4jMapper subject =
            new OpenAiResponsesLangChain4jMapper(mapper);

    @Test
    void mapsParametersAndExtras() {
        var include = mapper.createArrayNode().add("reasoning");
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(
                        "gpt-4.1",
                        0.2,
                        100,
                        null,
                        null,
                        List.of(),
                        null,
                        Map.of(
                                "previous_response_id", mapper.getNodeFactory().textNode("resp-1"),
                                "store", mapper.getNodeFactory().booleanNode(true),
                                "reasoning_effort", mapper.getNodeFactory().textNode("low"),
                                "include", include,
                                "service_tier", mapper.getNodeFactory().textNode("flex"))),
                false);

        var parameters = (OpenAiResponsesChatRequestParameters) subject
                .toRequest(request, "gpt-4.1", 100)
                .parameters();

        assertThat(parameters.modelName()).isEqualTo("gpt-4.1");
        assertThat(parameters.temperature()).isEqualTo(0.2);
        assertThat(parameters.maxOutputTokens()).isEqualTo(100);
        assertThat(parameters.previousResponseId()).isEqualTo("resp-1");
        assertThat(parameters.store()).isTrue();
        assertThat(parameters.reasoningEffort()).isEqualTo("low");
        assertThat(parameters.include()).containsExactly("reasoning");
        assertThat(parameters.serviceTier()).isEqualTo("flex");
    }

    @Test
    void mapsSpecificToolChoiceToRequired() {
        var choice = mapper.createObjectNode()
                .put("type", "function")
                .put("name", "get_weather");
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(
                        "gpt-4.1", null, null, null, null, List.of(), choice, Map.of()),
                false);

        var parameters = (OpenAiResponsesChatRequestParameters) subject
                .toRequest(request, "gpt-4.1", null)
                .parameters();

        assertThat(parameters.toolChoice()).isEqualTo(ToolChoice.REQUIRED);
    }

    @Test
    void mapsResponseUsage() {
        var response = ChatResponse.builder()
                .id("resp-1")
                .modelName("gpt-4.1")
                .aiMessage(AiMessage.from("Hello"))
                .tokenUsage(OpenAiTokenUsage.builder()
                        .inputTokenCount(10)
                        .outputTokenCount(20)
                        .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                                .cachedTokens(4)
                                .cacheWriteTokens(2)
                                .build())
                        .build())
                .finishReason(FinishReason.STOP)
                .build();

        var mapped = subject.toResponse(response);

        assertThat(mapped.id()).isEqualTo("resp-1");
        assertThat(mapped.model()).isEqualTo("gpt-4.1");
        assertThat(mapped.finishReason()).isEqualTo("stop");
        assertThat(mapped.usage()).isEqualTo(new AiTokenUsage(6, 20, 4, 2));
    }
}