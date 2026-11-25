package cn.tolim.pudans.core.ai.protocol.messages;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MessagesProtocolTest {

    @Test
    void buildsTheOfficialAnthropicModelWithProviderOptions() {
        var request = new AiChatProtocolRequest(
                "dashscope",
                "qwen3.7-max",
                "secret",
                "https://dashscope.aliyuncs.com/apps/anthropic",
                "/v1/messages",
                Map.of(
                        "x-api-key", "secret",
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14"),
                45_000,
                131_072,
                8_192,
                true,
                false,
                Map.of("enable_thinking", true));

        var model = new MessagesProtocol().create(request);

        assertThat(model).isInstanceOf(AnthropicChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen3.7-max");
        assertThat(model.getContextWindowSize()).isEqualTo(131_072);
        assertThat(ReflectionTestUtils.getField(model, "baseUrl"))
                .isEqualTo("https://dashscope.aliyuncs.com/apps/anthropic");
        var options = (GenerateOptions) ReflectionTestUtils.getField(model, "defaultOptions");
        assertThat(options).isNotNull();
        assertThat(options.getAdditionalHeaders())
                .containsEntry("anthropic-beta", "files-api-2025-04-14")
                .doesNotContainKeys("x-api-key", "anthropic-version");
        assertThat(options.getAdditionalBodyParams())
                .containsEntry("enable_thinking", true);
        assertThat(options.getMaxTokens()).isEqualTo(8_192);
        assertThat(options.getExecutionConfig().getTimeout())
                .isEqualTo(Duration.ofMillis(45_000));
    }

    @Test
    void appliesProviderProfileToHeadersBodyAndTokenLimit() {
        var request = new AiChatProtocolRequest(
                "custom",
                "custom-model",
                "secret",
                "https://api.example.com",
                "/v1/messages",
                Map.of(
                        "x-api-key", "secret",
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14"),
                45_000,
                131_072,
                8_192,
                true,
                false,
                Map.of());
        var profile = new MessagesProfile() {

            @Override
            public Map<String, String> additionalHeaders(Map<String, String> headers) {
                return Map.of("x-profile", "enabled");
            }

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

        var options = (GenerateOptions) ReflectionTestUtils.getField(model, "defaultOptions");
        assertThat(options).isNotNull();
        assertThat(options.getAdditionalHeaders()).containsEntry("x-profile", "enabled");
        assertThat(options.getAdditionalBodyParams()).containsEntry("profile_flag", true);
        assertThat(options.getMaxTokens()).isEqualTo(1_024);
    }
}
