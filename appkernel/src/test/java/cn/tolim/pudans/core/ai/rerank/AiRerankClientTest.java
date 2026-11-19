package cn.tolim.pudans.core.ai.rerank;

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

class AiRerankClientTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void sendsRequestWithBearerKeyAndProviderHeaders() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var provider = provider("https://api.example.com");
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var result = client(json("""
                        {"results":[
                          {"index":1,"relevance_score":0.75},
                          {"index":0,"relevance_score":0.25}
                        ]}
                        """))
                .rerank(provider, account(), "rerank-v1", new RerankRequest("安装", List.of("a", "b"), 2))
                .block();

        assertThat(result.results()).extracting(RerankResult.ScoredIndex::index).containsExactly(1, 0);
        assertThat(result.results()).extracting(RerankResult.ScoredIndex::score).containsExactly(0.75, 0.25);

        var request = captured.get();
        assertThat(request.url().toString()).isEqualTo("https://api.example.com/v1/rerank");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        assertThat(request.headers().getFirst("X-Org-Id")).isEqualTo("org-1");
        var body = body(request);
        assertThat(body).contains("\"model\":\"rerank-v1\"");
        assertThat(body).contains("\"query\":\"安装\"");
        assertThat(body).contains("\"documents\":[\"a\",\"b\"]");
        assertThat(body).contains("\"top_n\":2");
    }

    @Test
    void normalizesBaseUrlWithTrailingSlash() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        client(json("{\"results\":[{\"index\":0,\"relevance_score\":0.5}]}"))
                .rerank(provider("https://api.example.com/"), account(), "rerank-v1",
                        new RerankRequest("查询", List.of("a"), 1))
                .block();

        assertThat(captured.get().url().toString()).isEqualTo("https://api.example.com/v1/rerank");
    }

    @Test
    void omitsAuthorizationWhenApiKeyIsMissing() {
        when(accountService.apiKey(any())).thenReturn(Mono.empty());

        var result = client(json("{\"results\":[{\"index\":0,\"relevance_score\":0.5}]}"))
                .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                        new RerankRequest("查询", List.of("a"), 1))
                .block();

        assertThat(result.results()).hasSize(1);
        assertThat(captured.get().headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void rejectsResponsesWithoutResults() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("{}"))
                        .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                                new RerankRequest("查询", List.of("a"), 1)))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void rejectsNonNumericRelevanceScores() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("{\"results\":[{\"index\":0,\"relevance_score\":\"bad\"}]}"))
                        .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                                new RerankRequest("查询", List.of("a"), 1)))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void rejectsMalformedJsonResponses() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(json("not-json"))
                        .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                                new RerankRequest("查询", List.of("a"), 1)))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void mapsUpstreamHttpFailuresToBadGateway() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        StepVerifier.create(client(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"error\":\"boom\"}")
                                .build())
                        .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                                new RerankRequest("查询", List.of("a"), 1)))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void rejectsEmptyResponses() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var empty = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        StepVerifier.create(client(empty)
                        .rerank(provider("https://api.example.com"), account(), "rerank-v1",
                                new RerankRequest("查询", List.of("a"), 1)))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    private AiRerankClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new AiRerankClient(WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
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
