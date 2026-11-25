package cn.tolim.pudans.core.ai.protocol.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.messages.StandardMessagesProfile;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponsesProtocolTest {

    private final ResponsesProtocol protocol = new ResponsesProtocol();

    @Test
    void buildsTheOpenAiResponsesStreamingModelWithTokenLimit() {
        var model = protocol.create(request(1_024));

        assertThat(model).isInstanceOf(OpenAiResponsesStreamingChatModel.class);
        var parameters = (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();
        assertThat(parameters.modelName()).isEqualTo("gpt-5");
        assertThat(parameters.maxOutputTokens()).isEqualTo(1_024);
    }

    @Test
    void rejectsProfileForAnotherProtocol() {
        assertThatThrownBy(() -> protocol.create(request(1_024), new StandardMessagesProfile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("类型");
    }

    private static AiChatProtocolRequest request(Integer maxOutputTokens) {
        return new AiChatProtocolRequest(
                "openai",
                "gpt-5",
                "secret",
                "https://api.openai.com/v1",
                Map.of(),
                30_000,
                maxOutputTokens,
                Map.of());
    }
}