package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
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
public class HaystackExternalRagConnector extends AbstractExternalRagConnector {

    public HaystackExternalRagConnector(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        super(webClient);
    }

    @Override
    public KnowledgeBase.ExternalProvider provider() {
        return KnowledgeBase.ExternalProvider.HAYSTACK;
    }

    @Override
    public Mono<ExternalRagResult> retrieve(ExternalRagTarget target, ExternalRagRequest request) {
        var body = mapper().createObjectNode();
        body.put("query", request.query());
        body.put("top_k", request.topK());
        if (request.scoreThreshold() > 0) {
            body.put("score_threshold", request.scoreThreshold());
        }
        if (!request.filters().isEmpty()) {
            body.set("filters", mapper().valueToTree(request.filters()));
        }
        return postJson(target, baseUrl(target.endpoint()), body)
                .flatMap(root -> {
                    var code = firstInt(root, "code");
                    if (code != null && code != 0) {
                        return Mono.error(responseError(firstNonBlank(
                                firstText(root, "error"), firstText(root, "message"), "未知错误")));
                    }
                    return Mono.just(new ExternalRagResult(documents(root)));
                });
    }

    private static List<ExternalRagDocument> documents(JsonNode root) {
        var nodes = firstArray(root, "documents", "context_documents");
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        var documents = new ArrayList<ExternalRagDocument>(nodes.size());
        for (var node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            var content = firstText(node, "content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            var meta = node.get("meta");
            var metadata = meta == null || !meta.isObject() ? null : meta;
            var documentId = firstText(node, "id");
            if (!StringUtils.hasText(documentId)) {
                documentId = firstText(metadata, "file_path", "source", "source_id", "name");
            }
            var documentName = firstText(metadata, "file_path", "source", "name", "title");
            if (!StringUtils.hasText(documentName)) {
                documentName = documentId;
            }
            var pageNo = firstInt(metadata, "page_number", "page_no", "page");
            var headingPath = firstText(metadata, "heading_path", "title", "section");
            var score = firstDouble(node, "score");
            documents.add(new ExternalRagDocument(
                    documentId,
                    documentName,
                    firstText(node, "id"),
                    content,
                    score == null ? 0.0 : score,
                    pageNo,
                    headingPath));
        }
        return List.copyOf(documents);
    }
}