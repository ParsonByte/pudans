package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
public final class KnowledgeBaseContentRetriever implements ContentRetriever {

    private final KnowledgeBaseRetrievalService retrievalService;

    private final AiEmbeddingService embeddingService;

    private final RagContext context;

    private volatile List<Citation> citations = List.of();

    public KnowledgeBaseContentRetriever(
            KnowledgeBaseRetrievalService retrievalService,
            AiEmbeddingService embeddingService,
            RagContext context) {
        this.retrievalService = Objects.requireNonNull(retrievalService, "知识库检索服务不能为空");
        this.embeddingService = Objects.requireNonNull(embeddingService, "嵌入服务不能为空");
        this.context = Objects.requireNonNull(context, "RAG 上下文不能为空");
    }

    @Override
    public List<Content> retrieve(Query query) {
        var embedding = embeddingService.embedOne(query.text()).onErrorResume(error -> {
            log.warn("知识库查询向量生成失败，降级为关键词检索。", error);
            return Mono.just(new float[0]);
        }).block();
        var result = retrievalService
                .retrieve(new KnowledgeBaseRetrievalRequest(
                        context.userId(),
                        context.requestId(),
                        query.text(),
                        context.knowledgeBases(),
                        context.roleNames(),
                        context.deptId(),
                        embedding,
                        context.options(),
                        context.candidateLimit()))
                .block();
        if (result == null) {
            citations = List.of();
            return List.of();
        }
        citations = result.citations();
        if (!StringUtils.hasText(result.prompt())) {
            return List.of();
        }
        return List.of(Content.from(TextSegment.from(ChatPrompts.KNOWLEDGE_PREFIX + result.prompt())));
    }

    public List<Citation> citations() {
        return citations;
    }
}