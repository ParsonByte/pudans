package cn.tolim.pudans.core.infra.r2dbc.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.rag.RetrievalCandidate;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiKbEmbeddingStoreTest {

    @Mock
    AiKbChunkRepository chunkRepository;

    @Test
    void searchesTheVectorIndexAndKeepsMetadata() {
        var query = new float[] {0.1f, 0.2f};
        var embedding = new float[] {0.3f, 0.4f};
        var candidate = new RetrievalCandidate(11, 101, "kb-a", "manual.pdf", "安装前请备份。", 3, "2.1 安装", 0.82);
        when(chunkRepository.searchVectorCandidates(eq(List.of("kb-a")), any(float[].class), eq(5)))
                .thenReturn(Flux.just(new AiKbChunkRepository.VectorCandidate(candidate, embedding)));
        var store = new AiKbEmbeddingStore(chunkRepository);
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(query))
                .maxResults(5)
                .filter(MetadataFilterBuilder.metadataKey("kb_name").isIn("kb-a"))
                .build();

        var result = store.search(request);

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.score()).isEqualTo(0.82);
            assertThat(match.embeddingId()).isEqualTo("11");
            assertThat(match.embedding().vector()).containsExactly(embedding);
            assertThat(match.embedded().text()).isEqualTo("安装前请备份。");
            assertThat(match.embedded().metadata().getString("file_name")).isEqualTo("manual.pdf");
            assertThat(match.embedded().metadata().getLong("document_id")).isEqualTo(101L);
            assertThat(match.embedded().metadata().getInteger("page_no")).isEqualTo(3);
        });
    }

    @Test
    void appliesTheMinimumScore() {
        var candidate = new RetrievalCandidate(11, 101, "kb-a", "manual.pdf", "低分内容", 1, null, 0.2);
        when(chunkRepository.searchVectorCandidates(anyList(), any(float[].class), eq(5)))
                .thenReturn(Flux.just(new AiKbChunkRepository.VectorCandidate(
                        candidate, new float[] {0.3f, 0.4f})));
        var store = new AiKbEmbeddingStore(chunkRepository);
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.1f, 0.2f}))
                .maxResults(5)
                .minScore(0.5)
                .filter(MetadataFilterBuilder.metadataKey("kb_name").isIn("kb-a"))
                .build();

        assertThat(store.searchCandidates(request).block()).isEmpty();
    }

    @Test
    void updatesAnExistingChunkVector() {
        var embedding = new float[] {0.3f, 0.4f};
        when(chunkRepository.updateEmbedding(eq(7L), any(float[].class))).thenReturn(Mono.just(true));
        var store = new AiKbEmbeddingStore(chunkRepository);

        StepVerifier.create(store.updateEmbedding(7L, embedding)).verifyComplete();

        verify(chunkRepository).updateEmbedding(eq(7L), any(float[].class));
    }

    @Test
    void rejectsAVectorWriteForAMissingChunk() {
        var embedding = new float[] {0.3f, 0.4f};
        when(chunkRepository.updateEmbedding(eq(7L), any(float[].class))).thenReturn(Mono.just(false));
        var store = new AiKbEmbeddingStore(chunkRepository);

        StepVerifier.create(store.updateEmbedding(7L, embedding))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("7"))
                .verify();
    }
}