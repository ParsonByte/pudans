package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class RagflowExternalRagConnector extends AbstractExternalRagConnector {

    private static final String RETRIEVAL_PATH = "/api/v1/retrieval";

    public RagflowExternalRagConnector(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        super(webClient);
    }

    @Override
    public KnowledgeBase.ExternalProvider provider() {
        return KnowledgeBase.ExternalProvider.RAGFLOW;
    }

    @Override
    public Mono<ExternalRagResult> retrieve(ExternalRagTarget target, ExternalRagRequest request) {
        if (!StringUtils.hasText(target.apiKey())) {
            return Mono.error(configError("未配置 apiKey 或 credentialRef。"));
        }
        var dataset = firstNonBlank(target.dataset(), target.knowledgeBaseId());
        if (!StringUtils.hasText(dataset)) {
            return Mono.error(configError("未配置 dataset。"));
        }
        var body = mapper().createObjectNode();
        body.put("question", request.query());
        body.putArray("dataset_ids").add(dataset);
        body.put("top_k", request.topK());
        body.put("similarity_threshold", request.scoreThreshold());
        return postJson(target, baseUrl(target.endpoint()) + RETRIEVAL_PATH, body)
                .flatMap(root -> {
                    var code = firstInt(root, "code");
                    if (code != null && code != 0) {
                        return Mono.error(responseError(firstNonBlank(firstText(root, "message"), "未知错误")));
                    }
                    return Mono.just(new ExternalRagResult(documents(root)));
                });
    }

    private static List<ExternalRagDocument> documents(JsonNode root) {
        var chunks = firstArray(root, "data.chunks");
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        var documents = new ArrayList<ExternalRagDocument>(chunks.size());
        for (var chunk : chunks) {
            if (chunk == null || !chunk.isObject()) {
                continue;
            }
            var content = firstText(chunk, "content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            var documentId = firstText(chunk, "document_id", "id");
            var documentName = firstText(chunk, "document_name", "document_keyword", "document_id", "id");
            var score = firstDouble(chunk, "similarity", "score");
            documents.add(new ExternalRagDocument(
                    documentId,
                    documentName,
                    firstText(chunk, "id"),
                    content,
                    score == null ? 0.0 : score,
                    null,
                    null));
        }
        return List.copyOf(documents);
    }
}