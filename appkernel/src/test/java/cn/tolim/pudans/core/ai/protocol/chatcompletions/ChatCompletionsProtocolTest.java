package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCompletionsProtocolTest {

    private final ChatCompletionsProtocol protocol = new ChatCompletionsProtocol();

    @Test
    void createsOpenAiCompatibleModelsFromChannelSettings() {
        var model = protocol.create(request(
                "deepseek",
                "deepseek-v4-pro",
                "https://api.deepseek.com/v1",
                null,
                null,
                Map.of()));

        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("deepseek-v4-pro");
    }

    private static AiChatProtocolRequest request(
            String provider,
            String model,
            String baseUrl,
            Integer contextWindow,
            Integer maxOutputTokens,
            Map<String, Object> bodyParams) {
        return new AiChatProtocolRequest(
                provider,
                model,
                "secret",
                baseUrl,
                "/chat/completions",
                Map.of(),
                30_000,
                contextWindow,
                maxOutputTokens,
                false,
                false,
                bodyParams);
    }
}
