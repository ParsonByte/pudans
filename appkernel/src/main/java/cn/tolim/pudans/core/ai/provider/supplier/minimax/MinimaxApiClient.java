package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MinimaxApiClient extends AbstractMinimaxClient {

    static final String UPSTREAM_ERROR_CODE = "minimax-upstream-error";

    private static final String DEFAULT_ERROR = "MiniMax 服务调用失败。";

    private static final String DONE = "[DONE]";

    public MinimaxApiClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<JsonNode> get(
            AiProvider provider, AiProviderAccount account, String path, Map<String, String> query) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .get()
                                .uri(uri(provider, path, query))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("MiniMax 响应为空。", null))))
                        .map(this::parseJson))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<JsonNode> post(
            AiProvider provider, AiProviderAccount account, String path, JsonNode body) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .post()
                                .uri(AiProviderHttpSupport.endpoint(provider, path))
                                .headers(headers -> {
                                    headers.addAll(headers(provider, apiKey, MediaType.APPLICATION_JSON));
                                    headers.setContentType(MediaType.APPLICATION_JSON);
                                })
                                .bodyValue(AiProviderJsonSupport.write(mapper, body, MinimaxProtocol.NAME))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("MiniMax 响应为空。", null))))
                        .map(this::parseJson))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<JsonNode> delete(
            AiProvider provider, AiProviderAccount account, String path) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .delete()
                                .uri(AiProviderHttpSupport.endpoint(provider, path))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .defaultIfEmpty("{}"))
                        .map(this::parseJson))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<JsonNode> upload(
            AiProvider provider,
            AiProviderAccount account,
            String path,
            Map<String, String> fields,
            Map<String, String> extraHeaders,
            String filename,
            String contentType,
            byte[] content) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                            var body = new MultipartBodyBuilder();
                            fields.forEach((name, value) -> {
                                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                                    body.part(name, value);
                                }
                            });
                            body.part("file", new ByteArrayResource(content))
                                    .filename(filename(filename))
                                    .contentType(mediaType(contentType));
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, path))
                                    .headers(headers -> {
                                        headers.addAll(headers(provider, apiKey, MediaType.APPLICATION_JSON));
                                        if (extraHeaders != null) {
                                            extraHeaders.forEach(headers::set);
                                        }
                                    })
                                    .body(BodyInserters.fromMultipartData(body.build()))
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("MiniMax 响应为空。", null)));
                        })
                        .map(this::parseJson))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Flux<DataBuffer> content(
            AiProvider provider, AiProviderAccount account, String path, Map<String, String> query) {
        return Flux.defer(() -> providerAccounts
                        .apiKey(account)
                        .defaultIfEmpty("")
                        .flatMapMany(apiKey -> webClient()
                                .get()
                                .uri(uri(provider, path, query))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.ALL)))
                                .exchangeToFlux(response -> {
                                    if (response.statusCode().isError()) {
                                        return response.bodyToMono(String.class)
                                                .defaultIfEmpty("")
                                                .flatMapMany(error -> Flux.error(
                                                        upstreamError(response.statusCode().value(), error)));
                                    }
                                    return response.bodyToFlux(DataBuffer.class);
                                })))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Flux<JsonNode> postStream(
            AiProvider provider, AiProviderAccount account, String path, JsonNode body) {
        return Flux.defer(() -> providerAccounts
                        .apiKey(account)
                        .defaultIfEmpty("")
                        .flatMapMany(apiKey -> webClient()
                                .post()
                                .uri(AiProviderHttpSupport.endpoint(provider, path))
                                .headers(headers -> {
                                    headers.addAll(headers(provider, apiKey, MediaType.TEXT_EVENT_STREAM));
                                    headers.setContentType(MediaType.APPLICATION_JSON);
                                })
                                .bodyValue(AiProviderJsonSupport.write(mapper, body, MinimaxProtocol.NAME))
                                .retrieve()
                                .bodyToFlux(String.class)))
                .filter(data -> StringUtils.hasText(data) && !DONE.equals(data.strip()))
                .map(this::parseJson)
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private JsonNode parseJson(String body) {
        return validate(AiProviderHttpSupport.readObject(mapper, body, MinimaxProtocol.NAME));
    }

    private static URI uri(AiProvider provider, String path, Map<String, String> query) {
        var builder = UriComponentsBuilder.fromUriString(
                AiProviderHttpSupport.endpoint(provider, path));
        if (query != null) {
            query.forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    builder.queryParam(name, value);
                }
            });
        }
        return builder.build().encode().toUri();
    }

    private static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String filename(String filename) {
        return StringUtils.hasText(filename) ? filename.replaceAll("[\\r\\n]", "_") : "file";
    }
}
