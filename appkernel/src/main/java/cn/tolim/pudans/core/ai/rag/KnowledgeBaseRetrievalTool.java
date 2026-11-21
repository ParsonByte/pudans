package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public final class KnowledgeBaseRetrievalTool {

    public static final String NAME = "retrieve_knowledge";

    private static final int DEFAULT_LIMIT = 5;

    private static final int MAX_LIMIT = 20;

    private static final String NO_RESULT = "知识库未检索到相关内容。";

    private final KnowledgeRetrieval knowledgeRetrieval;

    private final Embeddings embeddings;

    private final RagContext context;

    private volatile List<Citation> citations = List.of();

    public KnowledgeBaseRetrievalTool(
            KnowledgeRetrieval knowledgeRetrieval,
            Embeddings embeddings,
            RagContext context) {
        this.knowledgeRetrieval = Objects.requireNonNull(knowledgeRetrieval, "知识库检索服务不能为空");
        this.embeddings = Objects.requireNonNull(embeddings, "嵌入服务不能为空");
        this.context = Objects.requireNonNull(context, "RAG 上下文不能为空");
    }

    public AiTool definition() {
        var mapper = JsonUtils.mapper();
        var parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        var properties = parameters.putObject("properties");
        properties.putObject("query")
                .put("type", "string")
                .put("description", "知识库检索问题");
        properties.putObject("limit")
                .put("type", "integer")
                .put("description", "返回片段数量，默认 5，最大 20");
        parameters.putArray("required").add("query");
        return new AiTool(
                NAME,
                "检索知识库中的相关片段，用于回答依赖已入库资料的问题。",
                parameters,
                null);
    }

    public HarnessToolkit.ToolHandler handler() {
        return (toolRequest, arguments) -> {
            var query = arguments == null ? null : arguments.get("query");
            if (query == null || !query.isTextual() || !StringUtils.hasText(query.textValue())) {
                return new HarnessToolkit.ToolResult("检索问题不能为空。", true);
            }
            try {
                var text = query.textValue().strip();
                var embedding = embeddings
                        .embedOne(text)
                        .onErrorResume(error -> Mono.just(new float[0]))
                        .defaultIfEmpty(new float[0])
                        .block();
                var result = knowledgeRetrieval
                        .retrieve(retrievalRequest(text, limit(arguments), embedding))
                        .block();
                if (result == null || result.citations().isEmpty()) {
                    citations = List.of();
                    return new HarnessToolkit.ToolResult(NO_RESULT, false);
                }
                citations = result.citations();
                return new HarnessToolkit.ToolResult(result.prompt(), false);
            } catch (RuntimeException error) {
                var message = error.getMessage();
                return new HarnessToolkit.ToolResult(
                        StringUtils.hasText(message) ? message : "知识库检索失败。", true);
            }
        };
    }

    public HarnessToolkit register(HarnessToolkit toolkit) {
        return toolkit.register(definition(), handler());
    }

    public List<Citation> citations() {
        return citations;
    }

    private KnowledgeBaseRetrievalRequest retrievalRequest(
            String query, int limit, float[] queryEmbedding) {
        var options = new RetrievalOptions(
                context.options().rrfK(),
                context.options().scoreThreshold(),
                limit,
                context.options().charBudget());
        return new KnowledgeBaseRetrievalRequest(
                context.userId(),
                context.requestId(),
                query,
                context.knowledgeBases(),
                context.roleNames(),
                context.deptId(),
                queryEmbedding,
                options,
                Math.max(context.candidateLimit(), limit));
    }

    private static int limit(JsonNode arguments) {
        var value = arguments == null ? null : arguments.get("limit");
        if (value == null || !value.canConvertToInt()) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(value.intValue(), 1, MAX_LIMIT);
    }
}
