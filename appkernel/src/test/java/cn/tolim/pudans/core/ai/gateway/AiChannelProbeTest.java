package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.protocol.systemone.SystemOneDecisionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxProvider;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiChannelProbeTest {

    @Mock
    AiProviders providers;

    @Mock
    SystemOneDecisionClient decisions;

    @Mock
    AiProvider provider;

    @Mock
    AiProviderHttp http;

    @Test
    void checksABuiltInProviderThroughTheSharedProviderHttp() {
        var channel = channel("deepseek", "messages");
        var credentials = Map.of("apiKey", "secret");
        when(providers.builtIn("deepseek")).thenReturn(Optional.of(provider));
        when(providers.require("deepseek")).thenReturn(provider);
        when(provider.modelsUrl("https://api.deepseek.com"))
                .thenReturn("https://api.deepseek.com/models");
        when(http.getResponse(
                        eq(URI.create("https://api.deepseek.com/models")),
                        eq(credentials),
                        any(Duration.class)))
                .thenReturn(Mono.just(new AiProviderHttp.UpstreamResponse(
                        200,
                        "{\"object\":\"list\",\"data\":[{\"id\":\"deepseek-flash\"}]}",
                        "request-1")));

        StepVerifier.create(probe().test(channel, credentials, "deepseek-flash"))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.protocol()).isEqualTo("messages");
                    assertThat(result.statusCode()).isEqualTo(200);
                    assertThat(result.requestId()).isEqualTo("request-1");
                    assertThat(result.message()).isEqualTo("ok");
                })
                .verifyComplete();
    }

    @Test
    void acceptsAManuallyAddedMediaModelMissingFromTheLanguageList() {
        var channel = channel("minimax", "messages");
        channel.setBaseUrl("https://api.minimax.cn");
        var credentials = Map.of("apiKey", "secret");
        var provider = new MiniMaxProvider(http);
        when(providers.builtIn("minimax")).thenReturn(Optional.of(provider));
        when(providers.require("minimax")).thenReturn(provider);
        when(http.getResponse(
                        eq(URI.create("https://api.minimax.cn/v1/models")),
                        eq(credentials),
                        any(Duration.class)))
                .thenReturn(Mono.just(new AiProviderHttp.UpstreamResponse(
                        200,
                        "{\"object\":\"list\",\"data\":[{\"id\":\"MiniMax-M3\"}]}",
                        "request-media")));

        StepVerifier.create(probe().test(channel, credentials, "MiniMax-H3"))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.statusCode()).isEqualTo(200);
                    assertThat(result.requestId()).isEqualTo("request-media");
                })
                .verifyComplete();
    }

    @Test
    void failsWhenTheRequestedModelIsMissingFromTheProviderCatalog() {
        var channel = channel("deepseek", "messages");
        var credentials = Map.of("apiKey", "secret");
        when(providers.builtIn("deepseek")).thenReturn(Optional.of(provider));
        when(providers.require("deepseek")).thenReturn(provider);
        when(provider.modelsUrl("https://api.deepseek.com"))
                .thenReturn("https://api.deepseek.com/models");
        when(provider.verifiesModelInList("deepseek-v4-pro")).thenReturn(true);
        when(http.getResponse(any(URI.class), eq(credentials), any(Duration.class)))
                .thenReturn(Mono.just(new AiProviderHttp.UpstreamResponse(
                        200,
                        "{\"object\":\"list\",\"data\":[{\"id\":\"deepseek-flash\"}]}",
                        null)));

        StepVerifier.create(probe().test(channel, credentials, "deepseek-v4-pro"))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.code()).isEqualTo("ai-channel-model-unavailable");
                    assertThat(result.message()).contains("所选模型");
                })
                .verifyComplete();
    }

    @Test
    void preservesTheUpstreamHttpStatusOnAuthenticationFailures() {
        var channel = channel("deepseek", "messages");
        var credentials = Map.of("apiKey", "secret");
        when(providers.builtIn("deepseek")).thenReturn(Optional.of(provider));
        when(providers.require("deepseek")).thenReturn(provider);
        when(provider.modelsUrl("https://api.deepseek.com"))
                .thenReturn("https://api.deepseek.com/models");
        when(http.getResponse(any(URI.class), eq(credentials), any(Duration.class)))
                .thenReturn(Mono.just(new AiProviderHttp.UpstreamResponse(
                        401, "{\"error\":\"invalid key\"}", "request-2")));

        StepVerifier.create(probe().test(channel, credentials, null))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.statusCode()).isEqualTo(401);
                    assertThat(result.requestId()).isEqualTo("request-2");
                    assertThat(result.code()).isEqualTo("ai-channel-provider-http-error");
                })
                .verifyComplete();
    }

    @Test
    void mapsSharedHttpFailuresToAProbeResult() {
        var channel = channel("deepseek", "messages");
        var credentials = Map.of("apiKey", "secret");
        when(providers.builtIn("deepseek")).thenReturn(Optional.of(provider));
        when(providers.require("deepseek")).thenReturn(provider);
        when(provider.modelsUrl("https://api.deepseek.com"))
                .thenReturn("https://api.deepseek.com/models");
        when(http.getResponse(any(URI.class), eq(credentials), any(Duration.class)))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "ai-provider-request-timeout",
                        "供应商接口请求超时。")));

        StepVerifier.create(probe().test(channel, credentials, null))
                .assertNext(result -> {
                    assertThat(result.success()).isFalse();
                    assertThat(result.code()).isEqualTo("ai-channel-provider-timeout");
                    assertThat(result.message()).contains("超时");
                })
                .verifyComplete();
    }

    @Test
    void requiresCredentialsBeforeProbing() {
        var channel = channel("deepseek", "messages");

        StepVerifier.create(probe().test(channel, Map.of(), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-channel-credentials-missing");
                })
                .verify();
    }

    @Test
    void rejectsUnsupportedProtocolsForConfiguredProviders() {
        var channel = channel("custom", "responses");
        when(providers.builtIn("custom")).thenReturn(Optional.empty());

        StepVerifier.create(probe().test(channel, Map.of("apiKey", "secret"), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-channel-protocol-unsupported");
                })
                .verify();
    }

    @Test
    void checksConfiguredMessagesProviderWithAnthropicAuthentication() {
        var channel = channel("custom", "messages");
        var credentials = Map.of("apiKey", "secret");
        var headers = Map.of("x-api-key", "secret", "anthropic-version", "2023-06-01");
        when(providers.builtIn("custom")).thenReturn(Optional.empty());
        when(providers.require("custom")).thenReturn(provider);
        when(provider.modelsUrlForChannel(channel)).thenReturn("https://api.anthropic.com/v1/models");
        when(provider.messagesHeaders("secret")).thenReturn(headers);
        when(http.getResponse(
                        eq(URI.create("https://api.anthropic.com/v1/models")),
                        eq(credentials),
                        eq(headers),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Mono.just(new AiProviderHttp.UpstreamResponse(
                        200,
                        "{\"data\":[{\"id\":\"claude-custom\"}]}",
                        "request-3")));

        StepVerifier.create(probe().test(channel, credentials, "claude-custom"))
                .assertNext(result -> {
                    assertThat(result.success()).isTrue();
                    assertThat(result.protocol()).isEqualTo("messages");
                    assertThat(result.requestId()).isEqualTo("request-3");
                })
                .verifyComplete();
    }

    private AiChannelProbe probe() {
        var properties = new AiProperties();
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setResponseTimeout(Duration.ofSeconds(2));
        properties.setDefaultTimeoutMs(30_000);
        return new AiChannelProbe(properties, http, providers, decisions);
    }

    private static AiChannelEntity channel(String provider, String protocol) {
        var channel = new AiChannelEntity();
        channel.setProvider(provider);
        channel.setProtocol(protocol);
        channel.setBaseUrl("https://api.deepseek.com");
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
