package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractAiProviderAdapter implements AiProviderAdapter {

    private final WebClient webClient;

    protected final AiProviderAccountService accountService;

    protected AbstractAiProviderAdapter(WebClient webClient, AiProviderAccountService accountService) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.accountService = Objects.requireNonNull(accountService, "供应商账号服务不能为空");
    }

    protected final Mono<String> post(
            AiProvider provider, AiProviderAccount account, String path, String body, boolean stream) {
        return post(provider, account, path, body, stream, Map.of());
    }

    protected final Mono<String> post(
            AiProvider provider,
            AiProviderAccount account,
            String path,
            String body,
            boolean stream,
            Map<String, String> requestHeaders) {
        return Mono.defer(() -> accountService.apiKey(account)
                .defaultIfEmpty("")
                .flatMap(apiKey -> request(provider, path, body, apiKey, stream, requestHeaders)
                        .retrieve()
                        .bodyToMono(String.class)));
    }

    protected final Flux<String> postStream(
            AiProvider provider, AiProviderAccount account, String path, String body) {
        return postStream(provider, account, path, body, Map.of());
    }

    protected final Flux<String> postStream(
            AiProvider provider,
            AiProviderAccount account,
            String path,
            String body,
            Map<String, String> requestHeaders) {
        return Flux.defer(() -> accountService.apiKey(account)
                .defaultIfEmpty("")
                .flatMapMany(apiKey -> request(provider, path, body, apiKey, true, requestHeaders)
                        .retrieve()
                        .bodyToFlux(String.class)));
    }

    protected abstract HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream);

    private WebClient.RequestHeadersSpec<?> request(
            AiProvider provider,
            String path,
            String body,
            String apiKey,
            boolean stream,
            Map<String, String> requestHeaders) {
        return webClient.post()
                .uri(AiProviderHttpSupport.endpoint(provider, path))
                .headers(headers -> {
                    headers.addAll(buildHeaders(provider, apiKey, stream));
                    requestHeaders.forEach(headers::set);
                })
                .bodyValue(body);
    }
}
