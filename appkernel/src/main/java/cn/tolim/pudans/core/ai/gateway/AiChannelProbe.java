package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.dto.AiChannelTestResult;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.systemone.SystemOneDecisionClient;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class AiChannelProbe {

    private static final Set<String> OPENAI_COMPATIBLE =
            Set.of("openai", "openai-compatible", "openai_compatible");

    private static final String API_KEY = "apiKey";

    private final WebClient webClient;

    private final AiProperties properties;

    private final AiProviders providers;

    private final SystemOneDecisionClient decisions;

    public AiChannelProbe(
            AiProperties properties,
            WebClient.Builder builder,
            AiProviders providers,
            SystemOneDecisionClient decisions) {
        this.properties = properties;
        this.providers = providers;
        this.decisions = decisions;
        this.webClient = builder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .option(
                                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                        (int) properties.getConnectTimeout().toMillis())
                                .responseTimeout(properties.getResponseTimeout())))
                .build();
    }

    public Mono<AiChannelTestResult> test(
            AiChannelEntity channel, Map<String, String> credentials) {
        var protocol = channel.getProtocol() == null ? "" : channel.getProtocol().trim().toLowerCase();
        if (AiProtocol.find(protocol).filter(value -> value == AiProtocol.SYSTEM_ONE).isPresent()) {
            return testSystemOne(channel, credentials, protocol);
        }
        if (!OPENAI_COMPATIBLE.contains(protocol)) {
            var parsed = AiProtocol.find(protocol);
            if (parsed.isEmpty() || parsed.get() != AiProtocol.CHAT_COMPLETIONS) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-protocol-unsupported",
                        "当前协议尚未实现渠道健康检查。"));
            }
        }
        if (!StringUtils.hasText(channel.getBaseUrl())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-channel-base-url-missing",
                    "AI 渠道缺少基础地址。"));
        }
        var apiKey = credentials == null ? null : credentials.get(API_KEY);
        if (!StringUtils.hasText(apiKey)) {
            return Mono.error(new ApiException(
                    HttpStatus.CONFLICT,
                    "ai-channel-credentials-missing",
                    "AI 渠道缺少 apiKey 凭据。"));
        }
        var timeout = Duration.ofMillis(
                channel.getTimeoutMs() == null
                        ? properties.getDefaultTimeoutMs()
                        : channel.getTimeoutMs());
        var url = stripTrailingSlash(channel.getBaseUrl().trim()) + "/models";
        return webClient
                .get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .exchangeToMono(response -> {
                    var requestId = response.headers().header("x-request-id").stream()
                            .findFirst()
                            .orElse(null);
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().thenReturn(new AiChannelTestResult(
                                true, protocol, response.statusCode().value(), requestId, null, "ok", Instant.now()));
                    }
                    return response.releaseBody().thenReturn(new AiChannelTestResult(
                            false,
                            protocol,
                            response.statusCode().value(),
                            requestId,
                            "ai-channel-provider-http-error",
                            "供应商返回非成功状态。",
                            Instant.now()));
                })
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.class, error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        error.getStatusCode().value(),
                        error.getHeaders().getFirst("x-request-id"),
                        "ai-channel-provider-http-error",
                        "供应商返回非成功状态。",
                        Instant.now())))
                .onErrorResume(WebClientRequestException.class, error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        null,
                        null,
                        "ai-channel-provider-unreachable",
                        "供应商请求失败。",
                        Instant.now())))
                .onErrorResume(java.util.concurrent.TimeoutException.class, error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        null,
                        null,
                        "ai-channel-provider-timeout",
                        "供应商请求超时。",
                        Instant.now())));
    }

    private Mono<AiChannelTestResult> testSystemOne(
            AiChannelEntity channel, Map<String, String> credentials, String protocol) {
        var provider = providers.require(channel.getProvider());
        var model = channel.getConfig() == null ? null : channel.getConfig().get("model");
        if (!(model instanceof String text) || text.isBlank()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-channel-model-missing",
                    "System One 渠道健康检查需要在渠道配置中提供 model。"));
        }
        return decisions
                .decide(
                        channel,
                        credentials,
                        text,
                        Map.of("probe", true),
                        Map.of("health", new SystemOneDecisionClient.YesNo(
                                "判断服务是否可用。", "可用", "不可用")),
                        provider.protocolProfile(AiProtocol.SYSTEM_ONE))
                .map(response -> new AiChannelTestResult(
                        true, protocol, 200, null, null, "ok", Instant.now()))
                .onErrorResume(error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        null,
                        null,
                        "ai-channel-provider-error",
                        error.getMessage(),
                        Instant.now())));
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
