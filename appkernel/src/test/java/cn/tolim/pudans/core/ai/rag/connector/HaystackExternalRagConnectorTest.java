package cn.tolim.pudans.core.ai.rag.connector;

import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.test.StepVerifier;

class HaystackExternalRagConnectorTest {

    private static final String RESPONSE = """
            {
              "code": 0,
              "documents": [
                {
                  "id": "doc-1",
                  "content": "段落一",
                  "score": 0.77,
                  "meta": {"file_path": "manual.pdf", "page_number": 3, "heading_path": "第一章"}
                },
                {
                  "id": "doc-2",
                  "content": "段落二",
                  "score": 1.2,
                  "meta": {"source": "notes.txt"}
                },
                {
                  "id": "doc-3",
                  "content": ""
                }
              ]
            }
            """;

    @Test
    void buildsRequestAndParsesDocuments() throws Exception {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new HaystackExternalRagConnector(recorder.webClient());

        var result = connector.retrieve(
                        target(null, "https://haystack.local/retrieve"),
                        new ExternalRagRequest("安装", 4, 0.25, Map.of("source", "manual")))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.documents()).hasSize(2);
        var first = result.documents().get(0);
        assertThat(first.documentId()).isEqualTo("doc-1");
        assertThat(first.documentName()).isEqualTo("manual.pdf");
        assertThat(first.pageNo()).isEqualTo(3);
        assertThat(first.headingPath()).isEqualTo("第一章");
        assertThat(first.score()).isEqualTo(0.77);
        var second = result.documents().get(1);
        assertThat(second.documentName()).isEqualTo("notes.txt");
        assertThat(second.score()).isEqualTo(1.2);

        var captured = recorder.request();
        assertThat(captured.method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.url().toString()).isEqualTo("https://haystack.local/retrieve");
        assertThat(captured.headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        var requestBody = JsonUtils.mapper().readTree(body(captured));
        assertThat(requestBody.path("query").asText()).isEqualTo("安装");
        assertThat(requestBody.path("top_k").asInt()).isEqualTo(4);
        assertThat(requestBody.path("score_threshold").asDouble()).isEqualTo(0.25);
        assertThat(requestBody.path("filters").path("source").asText()).isEqualTo("manual");
    }

    @Test
    void sendsBearerWhenApiKeyIsConfigured() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new HaystackExternalRagConnector(recorder.webClient());

        connector.retrieve(target("secret", "https://haystack.local/retrieve"), request()).block();

        assertThat(recorder.request().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer secret");
    }

    @Test
    void parsesContextDocumentsAsFallback() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"code\":0,\"context_documents\":["
                        + "{\"id\":\"ctx-1\",\"content\":\"窗口片段\",\"meta\":{\"source\":\"window.txt\"}}]}"));
        var connector = new HaystackExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target(null, "https://haystack.local/retrieve"), request()))
                .assertNext(result -> {
                    assertThat(result.documents()).hasSize(1);
                    assertThat(result.documents().get(0).content()).isEqualTo("窗口片段");
                    assertThat(result.documents().get(0).documentName()).isEqualTo("window.txt");
                })
                .verifyComplete();
    }

    @Test
    void mapsApiErrorCodesToMeaningfulExceptions() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"code\":-1,\"error\":\"pipeline failed\"}"));
        var connector = new HaystackExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target(null, "https://haystack.local/retrieve"), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("pipeline failed");
                })
                .verify();
    }

    @Test
    void rejectsInvalidEndpoint() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new HaystackExternalRagConnector(recorder.webClient());

        assertThatThrownBy(() -> connector.retrieve(target(null, "ftp://haystack.local"), request()))
                .isInstanceOf(ExternalRagException.class);
        assertThat(recorder.request()).isNull();
    }

    private static ExternalRagTarget target(String apiKey, String endpoint) {
        return new ExternalRagTarget(endpoint, null, null, apiKey, null);
    }

    private static ExternalRagRequest request() {
        return new ExternalRagRequest("安装", 4, 0.25, Map.of());
    }
}