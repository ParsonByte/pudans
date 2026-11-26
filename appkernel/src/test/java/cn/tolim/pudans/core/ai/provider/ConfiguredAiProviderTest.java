package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ConfiguredAiProviderTest {

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    @Mock
    AiProviderHttp http;

    @Test
    void discoversConfiguredMessagesModelsThroughAnthropicAuthentication() {
        var channel = channel("https://api.anthropic.com", "messages");
        var headers = Map.of(
                "x-api-key", "secret", "anthropic-version", "2023-06-01");
        when(http.getJson(
                        eq(URI.create("https://api.anthropic.com/v1/models")),
                        eq(CREDENTIALS),
                        eq(headers),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Mono.just(
                        "{\"data\":[{\"id\":\"claude-custom\",\"display_name\":\"Claude Custom\"}]}"));

        StepVerifier.create(new ConfiguredAiProvider("custom", http).discover(channel, CREDENTIALS))
                .assertNext(models -> {
                    assertThat(models).hasSize(1);
                    assertThat(models.getFirst().modelId()).isEqualTo("claude-custom");
                })
                .verifyComplete();
    }

    @Test
    void discoversConfiguredChatModelsThroughBearerAuthentication() {
        var channel = channel("https://gateway.example/v1", "chat-completions");
        when(http.getJson(
                        eq(URI.create("https://gateway.example/v1/models")),
                        eq(CREDENTIALS),
                        any(Duration.class)))
                .thenReturn(Mono.just("{\"data\":[{\"id\":\"chat-custom\"}]}"));

        StepVerifier.create(new ConfiguredAiProvider("custom", http).discover(channel, CREDENTIALS))
                .assertNext(models -> {
                    assertThat(models).hasSize(1);
                    assertThat(models.getFirst().modelId()).isEqualTo("chat-custom");
                })
                .verifyComplete();
    }

    private static AiChannelEntity channel(String baseUrl, String protocol) {
        var channel = new AiChannelEntity();
        channel.setProvider("custom");
        channel.setProtocol(protocol);
        channel.setBaseUrl(baseUrl);
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
