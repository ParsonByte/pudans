package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.dto.AiChannelTestResult;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.systemone.SystemOneDecisionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class AiChannelProbe {

    private static final String API_KEY = "apiKey";

    private final AiProviderHttp http;

    private final AiProperties properties;

    private final AiProviders providers;

    private final SystemOneDecisionClient decisions;

    public AiChannelProbe(
            AiProperties properties,
            AiProviderHttp http,
            AiProviders providers,
            SystemOneDecisionClient decisions) {
        this.properties = properties;
        this.http = http;
        this.providers = providers;
        this.decisions = decisions;
    }

    public Mono<AiChannelTestResult> test(
            AiChannelEntity channel, Map<String, String> credentials, String model) {
        var protocol = channel.getProtocol() == null ? "" : channel.getProtocol().trim().toLowerCase();
        if (AiProtocol.find(protocol).filter(value -> value == AiProtocol.SYSTEM_ONE).isPresent()) {
            return testSystemOne(channel, credentials, protocol);
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
        var builtIn = providers.builtIn(channel.getProvider()).isPresent();
        var parsed = AiProtocol.find(protocol);
        if (!builtIn
                && parsed.filter(value -> value == AiProtocol.CHAT_COMPLETIONS
                                || value == AiProtocol.MESSAGES)
                        .isEmpty()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-channel-protocol-unsupported",
                    "当前协议尚未实现渠道健康检查。"));
        }
        var timeout = Duration.ofMillis(
                channel.getTimeoutMs() == null
                        ? properties.getDefaultTimeoutMs()
                        : channel.getTimeoutMs());
        var provider = providers.require(channel.getProvider());
        var messages = !builtIn
                && parsed.filter(value -> value == AiProtocol.MESSAGES).isPresent();
        var uri = URI.create(
                builtIn ? provider.modelsUrl(channel.getBaseUrl()) : provider.modelsUrlForChannel(channel));
        var request = builtIn
                ? http.getResponse(uri, credentials, timeout)
                : http.getResponse(
                        uri,
                        credentials,
                        messages ? provider.messagesHeaders(apiKey) : Map.of(),
                        timeout,
                        messages ? AiProviderAuth.X_API_KEY : AiProviderAuth.BEARER);
        return request
                .map(response -> testResult(response, protocol, model))
                .onErrorResume(ApiException.class, error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        error.getStatusCode().value(),
                        null,
                        errorCode(error),
                        reason(error),
                        Instant.now())))
                .onErrorResume(error -> Mono.just(new AiChannelTestResult(
                        false,
                        protocol,
                        null,
                        null,
                        "ai-channel-provider-error",
                        "供应商请求失败。",
                        Instant.now())));
    }

    private static AiChannelTestResult testResult(
            AiProviderHttp.UpstreamResponse response, String protocol, String model) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new AiChannelTestResult(
                    false,
                    protocol,
                    response.statusCode(),
                    response.requestId(),
                    "ai-channel-provider-http-error",
                    "供应商返回非成功状态。",
                    Instant.now());
        }
        if (StringUtils.hasText(model) && !containsModel(response.body(), model)) {
            return new AiChannelTestResult(
                    false,
                    protocol,
                    response.statusCode(),
                    response.requestId(),
                    "ai-channel-model-unavailable",
                    "上游模型列表未返回所选模型。",
                    Instant.now());
        }
        return new AiChannelTestResult(
                true,
                protocol,
                response.statusCode(),
                response.requestId(),
                null,
                "ok",
                Instant.now());
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

    private static boolean containsModel(String body, String model) {
        try {
            var data = JsonUtils.mapper().readTree(body).path("data");
            if (!data.isArray()) {
                return false;
            }
            for (var item : data) {
                if (model.trim().equalsIgnoreCase(item.path("id").asText(""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception error) {
            return false;
        }
    }

    private static String reason(ApiException error) {
        return StringUtils.hasText(error.getReason()) ? error.getReason() : "供应商请求失败。";
    }

    private static String errorCode(ApiException error) {
        var code = error.getCode();
        if (code != null && code.endsWith("-timeout")) {
            return "ai-channel-provider-timeout";
        }
        if (code != null && code.endsWith("-unreachable")) {
            return "ai-channel-provider-unreachable";
        }
        return "ai-channel-provider-error";
    }
}
