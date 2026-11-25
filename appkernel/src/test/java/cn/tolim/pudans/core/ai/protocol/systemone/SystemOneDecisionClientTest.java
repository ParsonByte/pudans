package cn.tolim.pudans.core.ai.protocol.systemone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SystemOneDecisionClientTest {

    @Mock
    AiProviderHttp http;

    @Mock
    AiProviders providers;

    @Mock
    AiProvider provider;

    private SystemOneDecisionClient client;

    @BeforeEach
    void setUp() {
        client = new SystemOneDecisionClient(http, providers);
    }

    @Test
    void appliesProfileToPathHeadersAndBody() {
        when(providers.require("custom")).thenReturn(provider);
        when(provider.systemOneBaseUrl("https://api.example.com"))
                .thenReturn("https://api.example.com");
        when(provider.systemOnePath()).thenReturn("/v1/systemone");
        when(provider.systemOneHeaders()).thenReturn(Map.of("x-provider", "yes"));
        when(http.postJson(any(URI.class), anyMap(), anyMap(), any(), any(Duration.class)))
                .thenReturn(Mono.just("{\"model\":\"m\",\"answers\":{}}"));
        var channel = new AiChannelEntity();
        channel.setProvider("custom");
        channel.setBaseUrl("https://api.example.com");
        channel.setTimeoutMs(10_000);
        var credentials = Map.of("apiKey", "secret");
        var profile = new SystemOneProfile() {

            @Override
            public String requestPath(String providerPath) {
                return "/custom/decide";
            }

            @Override
            public Map<String, String> headers(Map<String, String> providerHeaders) {
                return Map.of("x-profile", "yes");
            }

            @Override
            public void customizeBody(Map<String, Object> body) {
                body.put("profile_flag", true);
            }
        };

        StepVerifier.create(client.decide(
                        channel, credentials, "m", Map.of(), Map.of(), profile))
                .assertNext(response -> assertThat(response.model()).isEqualTo("m"))
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create("https://api.example.com/custom/decide")),
                eq(credentials),
                eq(Map.of("x-profile", "yes")),
                argThat(body -> body instanceof Map<?, ?> values
                        && Boolean.TRUE.equals(values.get("profile_flag"))),
                eq(Duration.ofMillis(10_000)));
    }
}
