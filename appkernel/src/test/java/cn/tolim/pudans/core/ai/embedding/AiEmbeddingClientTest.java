package cn.tolim.pudans.core.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AiEmbeddingClientTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void sendsBearerKeyAndProviderHeadersAndParsesIndexOrder() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var provider = provider("https://api.example.com");
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var vectors = client(json("""
                        {"data":[
                          {"index":1,"embedding":[3.0,4.0]},
                          {"index":0,"embedding":[1.0,2.0]}
                        ]}
                        """))
                .embed(provider, account(), "text-embedding-3-small", List.of("a", "b"))
                .block();

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1f, 2f);
        assertThat(vectors.get(1)).containsExactly(3f, 4f);

        var request = captured.get();
        assertThat(request.url().toString()).isEqualTo("https://api.example.com/v1/embeddings");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        assertThat(request.headers().getFirst("X-Org-Id")).isEqualTo("org-1");
        var body = body(request);
        assertThat(body).contains("\"model\":\"text-embedding-3-small\"");
        assertThat(body).contains("\"input\":[\"a\",\"b\"]");
    }

    @Test
    void normalizesBaseUrlWithTrailingSlash() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        client(json("{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}"))
                .embed(provider("https://api.example.com/"), account(), "embedding-model", List.of("a"))
                .block();

        assertThat(captured.get().url().toString()).isEqualTo("https://api.example.com/v1/embeddings");
    }

    @Test
    void omitsAuthorizationWhenApiKeyIsMissing() {
        when(accountService.apiKey(any())).thenReturn(Mono.empty());

        var vectors = client(json("{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}"))
                .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a"))
                .block();

        assertThat(vectors).hasSize(1);
        assertThat(captured.get().headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void rejectsResponsesWithMismatchedVectorCount() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}"))
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a", "b")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    @Test
    void rejectsResponsesWithoutData() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("{}"))
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    @Test
    void rejectsNonNumericVectorElements() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("{\"data\":[{\"index\":0,\"embedding\":[\"bad\"]}]}"))
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    @Test
    void mapsUpstreamHttpFailuresToBadGateway() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"error\":\"boom\"}")
                                .build())
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    @Test
    void rejectsEmptyResponses() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var empty = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        StepVerifier.create(client(empty)
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    @Test
    void rejectsMalformedJsonResponses() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("not-json"))
                        .embed(provider("https://api.example.com"), account(), "embedding-model", List.of("a")))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-embedding-upstream-error"))
                .verify();
    }

    private AiEmbeddingClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new AiEmbeddingClient(WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }

    private static AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setBaseUrl(baseUrl);
        return provider;
    }

    private static AiProviderAccount account() {
        return new AiProviderAccount();
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        var apiException = (ApiException) error;
        assertThat(apiException.getStatusCode().value()).isEqualTo(status);
        assertThat(apiException.getCode()).isEqualTo(code);
    }
}
