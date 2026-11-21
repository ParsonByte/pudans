package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class OpenAiCompatibleScoringModelTest {

    @Test
    void returnsScoresInDocumentOrder() {
        var authorization = new AtomicReference<String>();
        var model = model(request -> {
            authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            assertThat(request.url().toString()).isEqualTo("https://api.example.com/v1/rerank");
            return Mono.just(json("""
                    {"results":[
                      {"index":1,"relevance_score":0.9},
                      {"index":0,"relevance_score":0.2}
                    ]}
                    """));
        });

        var response = model.scoreAll(
                List.of(TextSegment.from("第一段"), TextSegment.from("第二段")), "查询");

        assertThat(response.content()).containsExactly(0.2, 0.9);
        assertThat(authorization.get()).isEqualTo("Bearer api-key");
    }

    @Test
    void rejectsOutOfRangeOrDuplicateIndexes() {
        var model = model(request -> Mono.just(json("""
                {"results":[{"index":2,"relevance_score":0.9}]}
                """)));

        assertThatThrownBy(() -> model.scoreAll(List.of(TextSegment.from("第一段")), "查询"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("索引");
    }

    @Test
    void rejectsMissingResults() {
        var model = model(request -> Mono.just(json("{}")));

        assertThatThrownBy(() -> model.scoreAll(List.of(TextSegment.from("第一段")), "查询"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("results");
    }

    @Test
    void returnsNoScoresForEmptyDocuments() {
        var model = model(request -> {
            throw new AssertionError("空文档不应发起请求");
        });

        assertThat(model.scoreAll(List.of(), "查询").content()).isEmpty();
    }

    private static OpenAiCompatibleScoringModel model(
            Function<ClientRequest, Mono<ClientResponse>> exchange) {
        var provider = new AiProvider();
        provider.getSpec().setBaseUrl("https://api.example.com");
        var webClient = WebClient.builder().exchangeFunction(exchange::apply).build();
        return new OpenAiCompatibleScoringModel(
                webClient, new ObjectMapper(), provider, "api-key", "rerank-v1");
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}