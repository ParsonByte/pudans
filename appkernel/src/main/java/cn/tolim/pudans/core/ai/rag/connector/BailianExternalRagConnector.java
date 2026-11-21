package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Component
public class BailianExternalRagConnector extends AbstractExternalRagConnector {

    // 百炼检索路径尚未稳定公开，默认路径可通过 external.retrievePath 覆盖
    private static final String DEFAULT_RETRIEVE_PATH = "/api/v1/knowledge-bases/{knowledgeBaseId}/retrieve";

    private static final String KNOWLEDGE_BASE_ID_PLACEHOLDER = "{knowledgeBaseId}";

    public BailianExternalRagConnector(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        super(webClient);
    }

    @Override
    public KnowledgeBase.ExternalProvider provider() {
        return KnowledgeBase.ExternalProvider.BAILIAN;
    }

    @Override
    public Mono<ExternalRagResult> retrieve(ExternalRagTarget target, ExternalRagRequest request) {
        if (!StringUtils.hasText(target.apiKey())) {
            return Mono.error(configError("未配置 apiKey 或 credentialRef。"));
        }
        var knowledgeBaseId = firstNonBlank(target.knowledgeBaseId(), target.dataset());
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return Mono.error(configError("未配置 knowledgeBaseId。"));
        }
        var path = firstNonBlank(target.retrievePath(), DEFAULT_RETRIEVE_PATH);
        var resolvedPath = path.replace(
                KNOWLEDGE_BASE_ID_PLACEHOLDER,
                UriUtils.encodePathSegment(knowledgeBaseId, StandardCharsets.UTF_8));
        var uri = baseUrl(target.endpoint()) + (resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath);
        var body = mapper().createObjectNode();
        body.put("query", request.query());
        body.put("top_k", request.topK());
        body.put("score_threshold", request.scoreThreshold());
        return postJson(target, uri, body)
                .flatMap(root -> {
                    var nodes = firstArray(root, "output.nodes", "data.nodes", "result.nodes", "nodes");
                    if (nodes != null) {
                        return Mono.just(new ExternalRagResult(documents(nodes)));
                    }
                    var codeNode = root.get("code");
                    var code = codeNode != null && codeNode.isTextual() ? codeNode.textValue() : null;
                    var message = firstText(root, "message");
                    if (StringUtils.hasText(code) || StringUtils.hasText(message)) {
                        return Mono.error(responseError(firstNonBlank(message, code, "未返回检索结果")));
                    }
                    return Mono.just(new ExternalRagResult(List.of()));
                });
    }

    private static List<ExternalRagDocument> documents(ArrayNode nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        var documents = new ArrayList<ExternalRagDocument>(nodes.size());
        for (var node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            var content = firstText(node, "text", "content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            var meta = node.get("metadata");
            var metadata = meta == null || !meta.isObject() ? null : meta;
            var documentId = firstText(metadata, "doc_id", "_id", "document_id", "docId");
            if (!StringUtils.hasText(documentId)) {
                documentId = firstText(node, "id");
            }
            var documentName = firstText(metadata, "file_name", "doc_name", "document_name", "source", "title");
            if (!StringUtils.hasText(documentName)) {
                documentName = firstNonBlank(firstText(node, "title"), documentId);
            }
            var pageNo = firstInt(metadata, "page_number", "page_no", "page");
            var headingPath = firstText(metadata, "heading_path", "title", "section");
            var score = firstDouble(node, "score", "similarity");
            documents.add(new ExternalRagDocument(
                    documentId,
                    documentName,
                    firstText(metadata, "_id", "chunk_id", "id"),
                    content,
                    score == null ? 0.0 : score,
                    pageNo,
                    headingPath));
        }
        return List.copyOf(documents);
    }
}