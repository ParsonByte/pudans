package cn.tolim.pudans.core.ai.rag.connector;

import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class DifyExternalRagConnectorTest {

    private static final String RESPONSE = """
            {
              "query": {"content": "安装"},
              "records": [
                {
                  "segment": {
                    "id": "seg-1",
                    "document_id": "doc-1",
                    "content": "安装说明",
                    "document": {"id": "doc-1", "name": "manual.pdf"}
                  },
                  "score": 0.92
                },
                {
                  "segment": {
                    "id": "seg-2",
                    "document_id": "doc-2",
                    "content": "缺少文档名称"
                  },
                  "score": 0.51
                },
                {
                  "segment": {
                    "id": "seg-3",
                    "document_id": "doc-3",
                    "content": "   "
                  },
                  "score": 0.99
                }
              ]
            }
            """;

    @Test
    void buildsRequestAndParsesRecords() throws Exception {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new DifyExternalRagConnector(recorder.webClient());

        var result = connector.retrieve(target("dataset-1", "secret"), request()).block();

        assertThat(result).isNotNull();
        assertThat(result.documents()).hasSize(2);
        var first = result.documents().get(0);
        assertThat(first.documentId()).isEqualTo("doc-1");
        assertThat(first.documentName()).isEqualTo("manual.pdf");
        assertThat(first.chunkId()).isEqualTo("seg-1");
        assertThat(first.content()).isEqualTo("安装说明");
        assertThat(first.score()).isEqualTo(0.92);
        var second = result.documents().get(1);
        assertThat(second.documentId()).isEqualTo("doc-2");
        assertThat(second.documentName()).isEqualTo("doc-2");
        assertThat(second.score()).isEqualTo(0.51);

        var captured = recorder.request();
        assertThat(captured.method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.url().toString())
                .isEqualTo("https://dify.local/v1/datasets/dataset-1/retrieve");
        assertThat(captured.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret");
        var requestBody = JsonUtils.mapper().readTree(body(captured));
        assertThat(requestBody.path("query").asText()).isEqualTo("安装");
        var retrievalModel = requestBody.path("retrieval_model");
        assertThat(retrievalModel.path("search_method").asText()).isEqualTo("semantic_search");
        assertThat(retrievalModel.path("top_k").asInt()).isEqualTo(4);
        assertThat(retrievalModel.path("score_threshold_enabled").asBoolean()).isTrue();
        assertThat(retrievalModel.path("score_threshold").asDouble()).isEqualTo(0.25);
    }

    @Test
    void mapsHttpErrorsToMeaningfulExceptions() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                ExternalRagConnectorTestSupport.error(HttpStatus.BAD_GATEWAY, "upstream down"));
        var connector = new DifyExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target("dataset-1", "secret"), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("HTTP 502").contains("upstream down");
                })
                .verify();
    }

    @Test
    void rejectsMissingDatasetWithoutCallingHttp() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new DifyExternalRagConnector(recorder.webClient());
        var invalid = new ExternalRagTarget("https://dify.local", null, null, "secret", null);

        StepVerifier.create(connector.retrieve(invalid, request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("dataset");
                })
                .verify();
        assertThat(recorder.request()).isNull();
    }

    private static ExternalRagTarget target(String dataset, String apiKey) {
        return new ExternalRagTarget("https://dify.local/v1", dataset, null, apiKey, null);
    }

    private static ExternalRagRequest request() {
        return new ExternalRagRequest("安装", 4, 0.25, Map.of());
    }
}