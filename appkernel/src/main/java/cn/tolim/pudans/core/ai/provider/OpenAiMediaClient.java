package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class OpenAiMediaClient {

    private static final String CODE_UPSTREAM = "openai-media-upstream-error";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final ProviderAccounts providerAccounts;

    public OpenAiMediaClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.providerAccounts = Objects.requireNonNull(providerAccounts, "供应商账号服务不能为空");
    }

    public Mono<JsonNode> generateImage(
            AiProvider provider, AiProviderAccount account, JsonNode body) {
        return postJson(provider, account, "/v1/images/generations", body);
    }

    public Mono<byte[]> synthesize(
            AiProvider provider, AiProviderAccount account, JsonNode body) {
        return Mono.defer(() -> providerAccounts.apiKey(account)
                .defaultIfEmpty("")
                .flatMap(apiKey -> webClient
                        .post()
                        .uri(AiProviderHttpSupport.endpoint(provider, "/v1/audio/speech"))
                        .headers(headers -> {
                            headers.addAll(headers(provider, apiKey));
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        })
                        .bodyValue(JsonUtils.objectToJson(body))
                        .retrieve()
                        .bodyToMono(byte[].class)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<JsonNode> transcribe(
            AiProvider provider,
            AiProviderAccount account,
            String filename,
            String contentType,
            byte[] content,
            Map<String, String> fields) {
        return Mono.defer(() -> providerAccounts.apiKey(account)
                .defaultIfEmpty("")
                .flatMap(apiKey -> {
                    var body = new MultipartBodyBuilder();
                    fields.forEach((name, value) -> {
                        if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                            body.part(name, value);
                        }
                    });
                    body.part("file", new ByteArrayResource(content))
                            .filename(filename)
                            .contentType(mediaType(contentType));
                    return webClient
                            .post()
                            .uri(AiProviderHttpSupport.endpoint(provider, "/v1/audio/transcriptions"))
                            .headers(headers -> headers.addAll(headers(provider, apiKey)))
                            .body(BodyInserters.fromMultipartData(body.build()))
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(this::read);
                }))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    private Mono<JsonNode> postJson(
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

    private JsonNode read(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "上游返回空响应。");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "无法解析上游响应。", error);
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
            return new ApiException(status, CODE_UPSTREAM, "OpenAI 媒体接口调用失败。", error);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, "OpenAI 媒体接口调用失败。", error);
    }

    private static HttpHeaders headers(AiProvider provider, String apiKey) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
