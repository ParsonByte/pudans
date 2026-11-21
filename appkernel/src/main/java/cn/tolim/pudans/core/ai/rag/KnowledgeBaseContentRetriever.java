package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.core.ai.embedding.Embeddings;
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

    private final KnowledgeRetrieval knowledgeRetrieval;

    private final Embeddings embeddings;

    private final RagContext context;

    private volatile List<Citation> citations = List.of();

    public KnowledgeBaseContentRetriever(
            KnowledgeRetrieval knowledgeRetrieval,
            Embeddings embeddings,
            RagContext context) {
        this.knowledgeRetrieval = Objects.requireNonNull(knowledgeRetrieval, "知识库检索服务不能为空");
        this.embeddings = Objects.requireNonNull(embeddings, "嵌入服务不能为空");
        this.context = Objects.requireNonNull(context, "RAG 上下文不能为空");
    }

    @Override
    public List<Content> retrieve(Query query) {
        var embedding = embeddings.embedOne(query.text()).onErrorResume(error -> {
            log.warn("知识库查询向量生成失败，降级为关键词检索。", error);
            return Mono.just(new float[0]);
        }).block();
        var result = knowledgeRetrieval
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