package cn.tolim.pudans.core.ai.rag.connector;

import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.test.StepVerifier;

class RagflowExternalRagConnectorTest {

    private static final String RESPONSE = """
            {
              "code": 0,
              "message": "",
              "data": {
                "chunks": [
                  {
                    "id": "chunk-1",
                    "content": "检索片段",
                    "similarity": 0.83,
                    "document_id": "doc-1",
                    "document_name": "手册.pdf"
                  },
                  {
                    "id": "chunk-2",
                    "content": "关键词回退",
                    "score": 0.41,
                    "document_keyword": "备用.txt"
                  },
                  {
                    "id": "chunk-3",
                    "content": ""
                  }
                ]
              }
            }
            """;

    @Test
    void buildsRequestAndParsesChunks() throws Exception {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new RagflowExternalRagConnector(recorder.webClient());

        var result = connector.retrieve(target("dataset-1", "secret"), request()).block();

        assertThat(result).isNotNull();
        assertThat(result.documents()).hasSize(2);
        var first = result.documents().get(0);
        assertThat(first.documentId()).isEqualTo("doc-1");
        assertThat(first.documentName()).isEqualTo("手册.pdf");
        assertThat(first.chunkId()).isEqualTo("chunk-1");
        assertThat(first.score()).isEqualTo(0.83);
        var second = result.documents().get(1);
        assertThat(second.documentId()).isEqualTo("chunk-2");
        assertThat(second.documentName()).isEqualTo("备用.txt");
        assertThat(second.score()).isEqualTo(0.41);

        var captured = recorder.request();
        assertThat(captured.method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.url().toString()).isEqualTo("https://ragflow.local/api/v1/retrieval");
        assertThat(captured.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret");
        var requestBody = JsonUtils.mapper().readTree(body(captured));
        assertThat(requestBody.path("question").asText()).isEqualTo("安装");
        assertThat(requestBody.path("dataset_ids").get(0).asText()).isEqualTo("dataset-1");
        assertThat(requestBody.path("top_k").asInt()).isEqualTo(4);
        assertThat(requestBody.path("similarity_threshold").asDouble()).isEqualTo(0.25);
    }

    @Test
    void mapsApiErrorCodesToMeaningfulExceptions() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"code\":102,\"message\":\"dataset not found\",\"data\":false}"));
        var connector = new RagflowExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target("dataset-1", "secret"), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("dataset not found");
                })
                .verify();
    }

    @Test
    void returnsEmptyResultWhenDataIsFalse() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"code\":0,\"message\":\"\",\"data\":false}"));
        var connector = new RagflowExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target("dataset-1", "secret"), request()))
                .assertNext(result -> assertThat(result.documents()).isEmpty())
                .verifyComplete();
    }

    @Test
    void rejectsMissingApiKeyWithoutCallingHttp() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new RagflowExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target("dataset-1", null), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("apiKey");
                })
                .verify();
        assertThat(recorder.request()).isNull();
    }

    private static ExternalRagTarget target(String dataset, String apiKey) {
        return new ExternalRagTarget("https://ragflow.local", dataset, null, apiKey, null);
    }

    private static ExternalRagRequest request() {
        return new ExternalRagRequest("安装", 4, 0.25, Map.of());
    }
}