package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class GeminiMediaClient {

    private static final String CODE_UPSTREAM = "gemini-media-upstream-error";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final ProviderAccounts providerAccounts;

    public GeminiMediaClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.providerAccounts = Objects.requireNonNull(providerAccounts, "供应商账号服务不能为空");
    }

    public Mono<JsonNode> post(
            AiProvider provider, AiProviderAccount account, String path, JsonNode body) {
        return Mono.defer(() -> providerAccounts.apiKey(account)
                .defaultIfEmpty("")
                .flatMap(apiKey -> webClient
                        .post()
                        .uri(AiProviderHttpSupport.endpoint(provider, path))
                        .headers(headers -> {
                            headers.addAll(headers(provider, apiKey));
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        })
                        .bodyValue(JsonUtils.objectToJson(body))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::read)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<JsonNode> get(AiProvider provider, AiProviderAccount account, String path) {
        return Mono.defer(() -> providerAccounts.apiKey(account)
                .defaultIfEmpty("")
                .flatMap(apiKey -> webClient
                        .get()
                        .uri(AiProviderHttpSupport.endpoint(provider, path))
                        .headers(headers -> headers.addAll(headers(provider, apiKey)))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::read)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    private JsonNode read(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "Gemini 返回空响应。");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "无法解析 Gemini 响应。", error);
        }
    }

    private ApiException upstream(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        if (error instanceof WebClientResponseException response) {
            var status = response.getStatusCode().is4xxClientError()
                    ? HttpStatus.valueOf(response.getStatusCode().value())
                    : HttpStatus.BAD_GATEWAY;
            return new ApiException(status, CODE_UPSTREAM, "Gemini 媒体接口调用失败。", error);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "Gemini 媒体接口调用失败。", error);
    }

    private static HttpHeaders headers(AiProvider provider, String apiKey) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.set("x-goog-api-key", apiKey);
        }
        return headers;
    }
}
