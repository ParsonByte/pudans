package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class QianwenApiClient extends AbstractQianwenClient {

    static final String UPSTREAM_ERROR_CODE = "qianwen-media-upstream-error";

    private static final String DEFAULT_ERROR = "千问媒体接口调用失败。";

    private static final String HEADER_ASYNC = "X-DashScope-Async";

    public QianwenApiClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<JsonNode> post(
            AiProvider provider, AiProviderAccount account, String path, JsonNode body, boolean async) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                        .post()
                        .uri(AiProviderHttpSupport.endpoint(provider, path))
                        .headers(headers -> {
                            headers.addAll(headers(provider, apiKey));
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            if (async) {
                                headers.set(HEADER_ASYNC, "enable");
                            }
                        })
                        .bodyValue(JsonUtils.objectToJson(body))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::read)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<JsonNode> get(AiProvider provider, AiProviderAccount account, String path) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                        .get()
                        .uri(AiProviderHttpSupport.endpoint(provider, path))
                        .headers(headers -> headers.addAll(headers(provider, apiKey)))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::read)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<JsonNode> getExternal(String url) {
        return Mono.defer(() -> {
                    var uri = URI.create(url);
                    var scheme = uri.getScheme();
                    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                        throw new IllegalArgumentException("千问外部文件地址只允许 http 或 https");
                    }
                    return webClient()
                            .get()
                            .uri(uri)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(this::read);
                })
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<ProxyResponse> exchange(
            AiProvider provider,
            AiProviderAccount account,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> query,
            @Nullable String body) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                    var builder =
                            UriComponentsBuilder.fromUriString(AiProviderHttpSupport.endpoint(provider, path));
                    query.forEach((name, values) ->
                            values.forEach(value -> builder.queryParam(name, value)));
                    var spec = webClient()
                            .method(method)
                            .uri(builder.build().encode().toUri())
                            .headers(headers -> {
                                headers.addAll(headers(provider, apiKey));
                                if (body != null) {
                                    headers.setContentType(MediaType.APPLICATION_JSON);
                                }
                            });
                    if (body != null) {
                        spec.bodyValue(body);
                    }
                    return spec.exchangeToMono(this::proxyResponse);
                }))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    public Mono<ProxyResponse> postMultipart(
            AiProvider provider,
            AiProviderAccount account,
            String path,
            Map<String, String> fields,
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
                            .filename(filename)
                            .contentType(mediaType(contentType));
                    return webClient()
                            .post()
                            .uri(AiProviderHttpSupport.endpoint(provider, path))
                            .headers(headers -> headers.addAll(headers(provider, apiKey)))
                            .body(BodyInserters.fromMultipartData(body.build()))
                            .exchangeToMono(this::proxyResponse);
                }))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstream);
    }

    private Mono<ProxyResponse> proxyResponse(ClientResponse response) {
        return response
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(body -> new ProxyResponse(
                        response.statusCode().value(),
                        response.headers().contentType().orElse(null),
                        body));
    }

    public record ProxyResponse(
            int status, @Nullable MediaType contentType, byte[] body) {}
}
