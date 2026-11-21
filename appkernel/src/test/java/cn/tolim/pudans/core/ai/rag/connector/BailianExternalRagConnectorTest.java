package cn.tolim.pudans.core.ai.rag.connector;

import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.test.StepVerifier;

class BailianExternalRagConnectorTest {

    private static final String RESPONSE = """
            {
              "output": {
                "nodes": [
                  {
                    "text": "百炼片段",
                    "score": 0.66,
                    "metadata": {
                      "doc_id": "doc-1",
                      "file_name": "bailian.pdf",
                      "page_number": 5,
                      "heading_path": "概览",
                      "_id": "chunk-1"
                    }
                  },
                  {
                    "text": "无元数据片段",
                    "score": 0.4,
                    "id": "node-2"
                  }
                ]
              }
            }
            """;

    @Test
    void buildsDefaultRequestAndParsesNodes() throws Exception {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new BailianExternalRagConnector(recorder.webClient());

        var result = connector.retrieve(target("kb-1", null), request()).block();

        assertThat(result).isNotNull();
        assertThat(result.documents()).hasSize(2);
        var first = result.documents().get(0);
        assertThat(first.documentId()).isEqualTo("doc-1");
        assertThat(first.documentName()).isEqualTo("bailian.pdf");
        assertThat(first.chunkId()).isEqualTo("chunk-1");
        assertThat(first.pageNo()).isEqualTo(5);
        assertThat(first.headingPath()).isEqualTo("概览");
        var second = result.documents().get(1);
        assertThat(second.documentId()).isEqualTo("node-2");
        assertThat(second.documentName()).isEqualTo("node-2");

        var captured = recorder.request();
        assertThat(captured.method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.url().toString())
                .isEqualTo("https://dashscope.local/api/v1/knowledge-bases/kb-1/retrieve");
        assertThat(captured.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secret");
        var requestBody = JsonUtils.mapper().readTree(body(captured));
        assertThat(requestBody.path("query").asText()).isEqualTo("安装");
        assertThat(requestBody.path("top_k").asInt()).isEqualTo(4);
        assertThat(requestBody.path("score_threshold").asDouble()).isEqualTo(0.25);
    }

    @Test
    void usesConfiguredRetrievePathAndDataNodesFallback() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"data\":{\"nodes\":[{\"text\":\"片段\",\"score\":0.5}]}}"));
        var connector = new BailianExternalRagConnector(recorder.webClient());
        var target = new ExternalRagTarget(
                "https://dashscope.local", null, "kb-1", "secret", "/custom/{knowledgeBaseId}/query");

        StepVerifier.create(connector.retrieve(target, request()))
                .assertNext(result -> assertThat(result.documents()).hasSize(1))
                .verifyComplete();
        assertThat(recorder.request().url().toString())
                .isEqualTo("https://dashscope.local/custom/kb-1/query");
    }

    @Test
    void mapsTextualErrorCodeToMeaningfulException() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(
                json("{\"code\":\"InvalidApiKey\",\"message\":\"bad key\"}"));
        var connector = new BailianExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target("kb-1", null), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("bad key");
                })
                .verify();
    }

    @Test
    void rejectsMissingKnowledgeBaseIdWithoutCallingHttp() {
        var recorder = new ExternalRagConnectorTestSupport.Recorder(json(RESPONSE));
        var connector = new BailianExternalRagConnector(recorder.webClient());

        StepVerifier.create(connector.retrieve(target(null, null), request()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("knowledgeBaseId");
                })
                .verify();
        assertThat(recorder.request()).isNull();
    }

    private static ExternalRagTarget target(String knowledgeBaseId, String retrievePath) {
        return new ExternalRagTarget(
                "https://dashscope.local", null, knowledgeBaseId, "secret", retrievePath);
    }

    private static ExternalRagRequest request() {
        return new ExternalRagRequest("安装", 4, 0.25, Map.of());
    }
}