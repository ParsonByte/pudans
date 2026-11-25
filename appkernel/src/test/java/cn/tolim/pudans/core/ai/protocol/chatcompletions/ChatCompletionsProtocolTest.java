package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.messages.StandardMessagesProfile;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCompletionsProtocolTest {

    private final ChatCompletionsProtocol protocol = new ChatCompletionsProtocol();

    @Test
    void createsOpenAiCompatibleStreamingModelsFromChannelSettings() {
        var model = protocol.create(request("deepseek", "deepseek-v4-pro", Map.of()));

        assertThat(model).isInstanceOf(OpenAiStreamingChatModel.class);
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("deepseek-v4-pro");
    }

    @Test
    void appliesProviderProfileToBodyParametersAndCustomization() {
        var profile = new ChatCompletionsProfile() {

            @Override
            public Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
                var merged = new java.util.LinkedHashMap<>(bodyParams);
                merged.put("profile_flag", true);
                return Map.copyOf(merged);
            }

            @Override
            public void customize(
                    OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder) {
                builder.temperature(0.2);
            }
        };

        var model = protocol.create(
                request("custom", "custom-model", Map.of("reasoning_effort", "high")), profile);

        var parameters = (dev.langchain4j.model.openai.OpenAiChatRequestParameters)
                model.defaultRequestParameters();
        assertThat(parameters.customParameters())
                .containsEntry("profile_flag", true)
                .containsEntry("reasoning_effort", "high");
        assertThat(parameters.temperature()).isEqualTo(0.2);
    }

    @Test
    void rejectsProfileForAnotherProtocol() {
        assertThatThrownBy(() -> protocol.create(
                        request("custom", "custom-model", Map.of()),
                        new StandardMessagesProfile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("类型");
    }

    private static AiChatProtocolRequest request(
            String provider, String model, Map<String, Object> bodyParams) {
        return new AiChatProtocolRequest(
                provider,
                model,
                "secret",
                "https://api.example.com/v1",
                Map.of(),
                30_000,
                4_096,
                bodyParams);
    }
}