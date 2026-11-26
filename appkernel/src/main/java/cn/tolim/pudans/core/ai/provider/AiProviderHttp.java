package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AiProviderHttp {

    private static final String API_KEY = "apiKey";

    private static final int MAX_IN_MEMORY_BYTES = 100 * 1024 * 1024;

    private final WebClient webClient;

    private final AiProperties properties;

    public AiProviderHttp(AiProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .option(
                                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                        (int) properties.getConnectTimeout().toMillis())
                                .responseTimeout(properties.getResponseTimeout())))
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }

    // 无凭据 JSON 调用供同子系统内的真实服务复用同一 WebClient。
    public Mono<String> postJson(
            URI uri, Map<String, String> headers, Object body, Duration timeout) {
        return exchangeJson(
                webClient
                        .post()
                        .uri(uri)
                        .headers(requestHeaders -> {
                            if (headers != null) {
                                headers.forEach(requestHeaders::set);
                            }
                        })
                        .bodyValue(body),
                timeout,
                "供应商接口",
                "request");
    }

    public Mono<String> getJson(
            URI uri, Map<String, String> credentials, Duration timeout) {
        return getJson(uri, credentials, Map.of(), timeout);
    }

    public Mono<String> getJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchangeJson(
                webClient
                        .get()
                        .uri(uri)
                        .headers(requestHeaders -> {
                            requestHeaders.setBearerAuth(apiKey);
                            if (headers != null) {
                                headers.forEach(requestHeaders::set);
                            }
                        }),
                timeout,
                "模型目录",
                "discovery");
    }

    public Mono<String> postJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout) {
        return postJson(uri, credentials, headers, body, timeout, AiProviderAuth.BEARER);
    }

    public Mono<String> postJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchangeJson(
                webClient
                        .post()
                        .uri(uri)
                        .headers(requestHeaders ->
                                applyAuth(requestHeaders, apiKey, headers, auth))
                        .bodyValue(body),
                timeout,
                "供应商接口",
                "request");
    }

    public Mono<String> getJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchangeJson(
                webClient
                        .get()
                        .uri(uri)
                        .headers(requestHeaders ->
                                applyAuth(requestHeaders, apiKey, headers, auth)),
                timeout,
                "供应商接口",
                "request");
    }

    public Mono<UpstreamResponse> getResponse(
            URI uri, Map<String, String> credentials, Duration timeout) {
        return getResponse(uri, credentials, Map.of(), timeout, AiProviderAuth.BEARER);
    }

    public Mono<UpstreamResponse> getResponse(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return webClient
                .get()
                .uri(uri)
                .headers(requestHeaders -> applyAuth(requestHeaders, apiKey, headers, auth))
                .exchangeToMono(response -> response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new UpstreamResponse(
                                response.statusCode().value(),
                                body,
                                response.headers().header("x-request-id").stream()
                                        .findFirst()
                                        .orElse(null))))
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

    public Mono<String> deleteJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout) {
        return deleteJson(uri, credentials, headers, timeout, AiProviderAuth.BEARER);
    }

    public Mono<String> deleteJson(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchangeJson(
                webClient
                        .delete()
                        .uri(uri)
                        .headers(requestHeaders ->
                                applyAuth(requestHeaders, apiKey, headers, auth)),
                timeout,
                "供应商接口",
                "request");
    }

    public Mono<String> postMultipart(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            MultiValueMap<String, ?> parts,
            Duration timeout) {
        return postMultipart(uri, credentials, headers, parts, timeout, AiProviderAuth.BEARER);
    }

    public Mono<String> postMultipart(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            MultiValueMap<String, ?> parts,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return missingCredentials();
        }
        return exchangeJson(
                webClient
                        .post()
                        .uri(uri)
                        .headers(requestHeaders ->
                                applyAuth(requestHeaders, apiKey, headers, auth))
                        .body(BodyInserters.fromMultipartData(parts)),
                timeout,
                "供应商接口",
                "request");
    }

    public Flux<DataBuffer> getBinaryStream(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return Flux.error(missingCredentialsError());
        }
        return webClient
                .get()
                .uri(uri)
                .headers(requestHeaders -> applyAuth(requestHeaders, apiKey, headers, auth))
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class);
                    }
                    return response.releaseBody()
                            .thenMany(Flux.error(new ApiException(
                                    HttpStatus.BAD_GATEWAY,
                                    "ai-provider-request-http-error",
                                    "供应商接口返回 HTTP "
                                            + response.statusCode().value()
                                            + "。")));
                })
                .timeout(timeout == null ? responseTimeout() : timeout)
                .onErrorMap(WebClientRequestException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ai-provider-request-unreachable",
                        "无法连接供应商接口。",
                        error))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "ai-provider-request-timeout",
                                "供应商接口请求超时。",
                                error));
    }

    public Flux<DataBuffer> postBinaryJsonStream(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return Flux.error(missingCredentialsError());
        }
        return webClient
                .post()
                .uri(uri)
                .headers(requestHeaders -> {
                    requestHeaders.setBearerAuth(apiKey);
                    if (headers != null) {
                        headers.forEach(requestHeaders::set);
                    }
                })
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class);
                    }
                    return response.releaseBody()
                            .thenMany(Flux.error(new ApiException(
                                    HttpStatus.BAD_GATEWAY,
                                    "ai-provider-request-http-error",
                                    "供应商接口返回 HTTP "
                                            + response.statusCode().value()
                                            + "。")));
                })
                .timeout(timeout == null ? responseTimeout() : timeout)
                .onErrorMap(WebClientRequestException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ai-provider-request-unreachable",
                        "无法连接供应商接口。",
                        error))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "ai-provider-request-timeout",
                                "供应商接口请求超时。",
                                error));
    }

    public Flux<ServerSentEvent<String>> postSse(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout) {
        return postSse(uri, credentials, headers, body, timeout, AiProviderAuth.BEARER);
    }

    public Flux<ServerSentEvent<String>> postSse(
            URI uri,
            Map<String, String> credentials,
            Map<String, String> headers,
            Object body,
            Duration timeout,
            AiProviderAuth auth) {
        var apiKey = apiKey(credentials);
        if (apiKey == null) {
            return Flux.error(missingCredentialsError());
        }
        return webClient
                .post()
                .uri(uri)
                .headers(requestHeaders -> applyAuth(requestHeaders, apiKey, headers, auth))
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(
                                new ParameterizedTypeReference<ServerSentEvent<String>>() {});
                    }
                    return response.releaseBody()
                            .thenMany(Flux.error(new ApiException(
                                    HttpStatus.BAD_GATEWAY,
                                    "ai-provider-request-http-error",
                                    "供应商接口返回 HTTP "
                                            + response.statusCode().value()
                                            + "。")));
                })
                .timeout(timeout == null ? responseTimeout() : timeout)
                .onErrorMap(WebClientRequestException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ai-provider-request-unreachable",
                        "无法连接供应商接口。",
                        error))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "ai-provider-request-timeout",
                                "供应商接口请求超时。",
                                error));
    }

    private Mono<String> exchangeJson(
            WebClient.RequestHeadersSpec<?> request,
            Duration timeout,
            String operation,
            String codePrefix) {
        return exchange(
                request,
                timeout,
                operation,
                codePrefix,
                response -> response.bodyToMono(String.class).defaultIfEmpty(""));
    }

    private <T> Mono<T> exchange(
            WebClient.RequestHeadersSpec<?> request,
            Duration timeout,
            String operation,
            String codePrefix,
            Function<ClientResponse, Mono<T>> body) {
        return request
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return body.apply(response);
                    }
                    return response.releaseBody().then(Mono.error(new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "ai-provider-" + codePrefix + "-http-error",
                            "供应商" + operation + "返回 HTTP " + response.statusCode().value() + "。")));
                })
                .timeout(timeout == null ? responseTimeout() : timeout)
                .onErrorMap(WebClientRequestException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ai-provider-" + codePrefix + "-unreachable",
                        "无法连接供应商" + operation + "。",
                        error))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        error -> new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "ai-provider-" + codePrefix + "-timeout",
                                "供应商" + operation + "请求超时。",
                                error));
    }

    private static String apiKey(Map<String, String> credentials) {
        var apiKey = credentials == null ? null : credentials.get(API_KEY);
        return StringUtils.hasText(apiKey) ? apiKey.trim() : null;
    }

    private static void applyAuth(
            HttpHeaders headers,
            String apiKey,
            Map<String, String> additional,
            AiProviderAuth auth) {
        if (auth == AiProviderAuth.X_API_KEY) {
            headers.set("x-api-key", apiKey);
        } else {
            headers.setBearerAuth(apiKey);
        }
        if (additional != null) {
            additional.forEach(headers::set);
        }
    }

    private static <T> Mono<T> missingCredentials() {
        return Mono.error(missingCredentialsError());
    }

    private static ApiException missingCredentialsError() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ai-provider-credentials-missing",
                "AI 渠道缺少 apiKey 凭据。");
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

    public static ApiException invalidRequestResponse(Throwable error) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "ai-provider-request-invalid-response",
                "供应商接口返回了无法解析的数据。",
                error);
    }

    public record UpstreamResponse(int statusCode, String body, String requestId) {}
}
