package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
public class AiProviderHttp {

    private static final String API_KEY = "apiKey";

    private final WebClient webClient;

    private final AiProperties properties;

    public AiProviderHttp(AiProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder.clone()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .option(
                                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                        (int) properties.getConnectTimeout().toMillis())
                                .responseTimeout(properties.getResponseTimeout())))
                .build();
    }

    public Mono<String> getJson(
            URI uri, Map<String, String> credentials, Duration timeout) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchange(
                webClient.get().uri(uri).headers(headers -> headers.setBearerAuth(apiKey)),
                timeout);
    }

    public Mono<String> postJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchange(
                webClient
                        .post()
                        .uri(uri)
                        .headers(requestHeaders -> {
                            requestHeaders.setBearerAuth(apiKey);
                            if (headers != null) {
                                headers.forEach(requestHeaders::set);
                            }
                        })
                        .bodyValue(body),
                timeout);
    }

    private Mono<String> exchange(
            WebClient.RequestHeadersSpec<?> request, Duration timeout) {
        return request
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    }
                    return response.releaseBody().then(Mono.error(new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "ai-provider-discovery-http-error",
                            "供应商模型目录返回 HTTP " + response.statusCode().value() + "。")));
                })
                .timeout(timeout == null ? responseTimeout() : timeout)
                .onErrorMap(WebClientRequestException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ai-provider-discovery-unreachable",
                        "无法连接供应商模型目录。",
                        error))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "ai-provider-discovery-timeout",
                                "供应商模型目录请求超时。",
                                error));
    }

    private static String apiKey(Map<String, String> credentials) {
        var apiKey = credentials == null ? null : credentials.get(API_KEY);
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }

    private static Mono<String> missingCredentials() {
        return Mono.error(new ApiException(
                HttpStatus.CONFLICT,
                "ai-provider-credentials-missing",
                "AI 渠道缺少 apiKey 凭据。"));
    }

    public Duration responseTimeout() {
        return properties.getResponseTimeout();
    }

    public static ApiException invalidResponse(Throwable error) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "ai-provider-discovery-invalid-response",
                "供应商模型目录返回了无法解析的数据。",
                error);
    }
}
