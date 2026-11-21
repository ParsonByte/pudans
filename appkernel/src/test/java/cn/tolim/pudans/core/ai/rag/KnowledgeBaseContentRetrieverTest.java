package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseContentRetrieverTest {

    @Mock
    KnowledgeRetrieval knowledgeRetrieval;

    @Mock
    Embeddings embeddings;

    private KnowledgeBaseContentRetriever retriever;

    @BeforeEach
    void setUp() {
        var context = new RagContext(
                "user-1",
                "req-1",
                List.of(new KnowledgeBase()),
                Set.of("member"),
                "dept-a",
                new RetrievalOptions(60, 0.3, 10, 4000),
                30);
        retriever = new KnowledgeBaseContentRetriever(knowledgeRetrieval, embeddings, context);
    }

    @Test
    void returnsTheKnowledgePromptAndKeepsCitations() {
        var vector = new float[] {0.1f, 0.2f};
        var citation = new Citation(1, 101, "manual.pdf", 3, "2.1 安装", 0.9, "先备份。");
        when(embeddings.embedOne("怎么安装")).thenReturn(Mono.just(vector));
        when(knowledgeRetrieval.retrieve(any(KnowledgeBaseRetrievalRequest.class)))
                .thenReturn(Mono.just(new KnowledgeBaseRetrievalResult(
                        "【知识库资料】\n[1]《manual.pdf》\n先备份。", List.of(citation))));

        var contents = retriever.retrieve(Query.from("怎么安装"));

        assertThat(contents).singleElement().satisfies(content -> assertThat(content.textSegment().text())
                .contains("以下资料仅作为事实参考")
                .contains("[1]《manual.pdf》"));
        assertThat(retriever.citations()).containsExactly(citation);
        var request = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(knowledgeRetrieval).retrieve(request.capture());
        assertThat(request.getValue().query()).isEqualTo("怎么安装");
        assertThat(request.getValue().queryEmbedding()).containsExactly(vector);
        assertThat(request.getValue().requestId()).isEqualTo("req-1");
        assertThat(request.getValue().candidateLimit()).isEqualTo(30);
    }

    @Test
    void fallsBackToKeywordRetrievalWhenEmbeddingFails() {
        when(embeddings.embedOne("怎么安装")).thenReturn(Mono.error(new IllegalStateException("失败")));
        when(knowledgeRetrieval.retrieve(any(KnowledgeBaseRetrievalRequest.class)))
                .thenReturn(Mono.just(new KnowledgeBaseRetrievalResult("", List.of())));

        assertThat(retriever.retrieve(Query.from("怎么安装"))).isEmpty();

        var request = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(knowledgeRetrieval).retrieve(request.capture());
        assertThat(request.getValue().queryEmbedding()).isEmpty();
        assertThat(retriever.citations()).isEmpty();
    }
}