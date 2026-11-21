package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class DifyExternalRagConnector extends AbstractExternalRagConnector {

    private static final String SEARCH_METHOD = "semantic_search";

    public DifyExternalRagConnector(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        super(webClient);
    }

    @Override
    public KnowledgeBase.ExternalProvider provider() {
        return KnowledgeBase.ExternalProvider.DIFY;
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
        var uri = UriComponentsBuilder.fromUriString(baseUrl(target.endpoint()))
                .pathSegment("datasets", dataset, "retrieve")
                .build()
                .toUriString();
        var body = mapper().createObjectNode();
        body.put("query", request.query());
        var retrievalModel = body.putObject("retrieval_model")
                .put("search_method", SEARCH_METHOD)
                .put("top_k", request.topK());
        if (request.scoreThreshold() > 0) {
            retrievalModel.put("score_threshold_enabled", true)
                    .put("score_threshold", request.scoreThreshold());
        } else {
            retrievalModel.put("score_threshold_enabled", false);
        }
        return postJson(target, uri, body)
                .map(root -> new ExternalRagResult(documents(root)));
    }

    private static List<ExternalRagDocument> documents(JsonNode root) {
        var records = firstArray(root, "records");
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        var documents = new ArrayList<ExternalRagDocument>(records.size());
        for (var record : records) {
            if (record == null || !record.isObject()) {
                continue;
            }
            var segment = record.get("segment");
            if (segment == null || !segment.isObject()) {
                continue;
            }
            var content = firstText(segment, "content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            var document = segment.get("document");
            var documentId = firstText(segment, "document_id", "id");
            if (!StringUtils.hasText(documentId) && document != null && document.isObject()) {
                documentId = firstText(document, "id");
            }
            var documentName = document == null || !document.isObject() ? null : firstText(document, "name");
            if (!StringUtils.hasText(documentName)) {
                documentName = documentId;
            }
            var score = firstDouble(record, "score");
            documents.add(new ExternalRagDocument(
                    documentId,
                    documentName,
                    firstText(segment, "id"),
                    content,
                    score == null ? 0.0 : score,
                    null,
                    null));
        }
        return List.copyOf(documents);
    }
}