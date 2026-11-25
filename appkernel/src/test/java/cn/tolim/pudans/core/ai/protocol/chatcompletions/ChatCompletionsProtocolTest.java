package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.messages.StandardMessagesProfile;
import io.agentscope.core.formatter.Formatter;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIRequest;
import io.agentscope.extensions.model.openai.dto.OpenAIResponse;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void usesStandardProfileWithoutProviderSpecificFormatter() {
        var model = protocol.create(request(
                "deepseek",
                "deepseek-v4-pro",
                "https://api.deepseek.com/v1",
                null,
                null,
                Map.of()));

        assertThat(ReflectionTestUtils.getField(model, "formatter"))
                .isExactlyInstanceOf(OpenAIChatFormatter.class);
    }

    @Test
    void appliesProviderProfileToFormatterHeadersAndBody() {
        var formatter = new OpenAIChatFormatter();
        var profile = new ChatCompletionsProfile() {

            @Override
            public Formatter<OpenAIMessage, OpenAIResponse, OpenAIRequest> formatter() {
                return formatter;
            }

            @Override
            public Map<String, String> additionalHeaders(Map<String, String> headers) {
                return Map.of("x-profile", "enabled");
            }

            @Override
            public Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
                return Map.of("profile_flag", true);
            }
        };

        var model = protocol.create(request(
                "custom",
                "custom-model",
                "https://api.example.com/v1",
                null,
                null,
                Map.of()), profile);

        assertThat(ReflectionTestUtils.getField(model, "formatter")).isSameAs(formatter);
        var options = (GenerateOptions) ReflectionTestUtils.getField(model, "configuredOptions");
        assertThat(options).isNotNull();
        assertThat(options.getAdditionalHeaders()).containsEntry("x-profile", "enabled");
        assertThat(options.getAdditionalBodyParams()).containsEntry("profile_flag", true);
    }

    @Test
    void rejectsProfileForAnotherProtocol() {
        assertThatThrownBy(() -> protocol.create(
                request(
                        "custom",
                        "custom-model",
                        "https://api.example.com/v1",
                        null,
                        null,
                        Map.of()),
                new StandardMessagesProfile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("类型");
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
