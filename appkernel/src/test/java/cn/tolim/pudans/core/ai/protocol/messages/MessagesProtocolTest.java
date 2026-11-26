package cn.tolim.pudans.core.ai.protocol.messages;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MessagesProtocolTest {

    @Test
    void buildsTheOfficialAnthropicStreamingModelWithProviderOptions() {
        var request = new AiChatProtocolRequest(
                "custom",
                "custom-model",
                "secret",
                "https://api.example.com/apps/anthropic",
                Map.of(
                        "x-api-key", "secret",
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14"),
                45_000,
                8_192,
                Map.of("enable_thinking", true));

        var model = new MessagesProtocol().create(request);

        assertThat(model).isInstanceOf(AnthropicStreamingChatModel.class);
        var parameters = (AnthropicChatRequestParameters) model.defaultRequestParameters();
        assertThat(parameters.modelName()).isEqualTo("custom-model");
        assertThat(parameters.maxOutputTokens()).isEqualTo(8_192);
        assertThat((Map<String, Object>) ReflectionTestUtils.getField(model, "customParameters"))
                .containsEntry("enable_thinking", true);
    }

    @Test
    void appliesProviderProfileToTokenLimitAndBody() {
        var request = new AiChatProtocolRequest(
                "custom",
                "custom-model",
                "secret",
                "https://api.example.com",
                Map.of(
                        "x-api-key", "secret",
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14"),
                45_000,
                8_192,
                Map.of());
        var profile = new MessagesProfile() {

            @Override
            public Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
                return Map.of("profile_flag", true);
            }

            @Override
            public Integer maxOutputTokens(Integer requested) {
                return 1_024;
            }
        };

        var model = new MessagesProtocol().create(request, profile);

        var parameters = (AnthropicChatRequestParameters) model.defaultRequestParameters();
        assertThat(parameters.maxOutputTokens()).isEqualTo(1_024);
        assertThat((Map<String, Object>) ReflectionTestUtils.getField(model, "customParameters"))
                .containsEntry("profile_flag", true);
    }

    @Test
    void defaultsToTheLegacyOutputLimitWhenTheModelHasNoLimit() {
        var request = new AiChatProtocolRequest(
                "custom",
                "custom-model",
                "secret",
                "https://api.example.com/v1",
                Map.of(),
                45_000,
                null,
                Map.of());

        var model = new MessagesProtocol().create(request);

        var parameters = (AnthropicChatRequestParameters) model.defaultRequestParameters();
        assertThat(parameters.maxOutputTokens()).isEqualTo(4_096);
    }

    @Test
    void filtersAuthenticationHeadersFromProfileHeaders() {
        var headers = new StandardMessagesProfile().additionalHeaders(Map.of(
                "x-api-key", "secret",
                "anthropic-version", "2023-06-01",
                "Authorization", "Bearer secret",
                "anthropic-beta", "files-api-2025-04-14"));

        assertThat(headers)
                .containsEntry("anthropic-beta", "files-api-2025-04-14")
                .doesNotContainKeys("x-api-key", "anthropic-version", "Authorization");
    }
}