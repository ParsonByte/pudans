package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.embedding.EmbeddingBatch;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbEmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiChunkEmbeddingsTest {

    @Mock
    AiSettings aiSettings;

    @Mock
    Embeddings embeddings;

    @Mock
    AiKbEmbeddingStore embeddingStore;

    @InjectMocks
    AiChunkEmbeddings service;

    @Test
    void updatesEachChunkWithItsVectorInOrder() {
        var first = chunk(1L, "first");
        var second = chunk(2L, "second");
        var firstVector = new float[] {0.1f, 0.2f};
        var secondVector = new float[] {0.3f, 0.4f};
        when(embeddings.embed(List.of("first", "second"), "kb-model"))
                .thenReturn(Mono.just(new EmbeddingBatch(
                        "text-embedding-3-small", 2, List.of(firstVector, secondVector))));
        when(embeddingStore.updateEmbedding(1L, firstVector)).thenReturn(Mono.empty());
        when(embeddingStore.updateEmbedding(2L, secondVector)).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of(first, second), "kb-model")).verifyComplete();

        var inOrder = inOrder(embeddingStore);
        inOrder.verify(embeddingStore).updateEmbedding(1L, firstVector);
        inOrder.verify(embeddingStore).updateEmbedding(2L, secondVector);
    }

    @Test
    void fallsBackToSettingModelWhenKnowledgeBaseModelIsBlank() {
        var vector = new float[] {1f, 2f};
        when(aiSettings.spec()).thenReturn(Mono.just(setting("setting-model")));
        when(embeddings.embed(List.of("hello"), "setting-model"))
                .thenReturn(Mono.just(new EmbeddingBatch("text-embedding-3-small", 2, List.of(vector))));
        when(embeddingStore.updateEmbedding(1L, vector)).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of(chunk(1L, "hello")), " ")).verifyComplete();

        verify(embeddings).embed(List.of("hello"), "setting-model");
        verify(embeddings, never()).embed(anyList());
    }

    @Test
    void skipsEmbeddingWhenKnowledgeBaseModelIsMissing() {
        when(embeddings.embed(anyList(), eq("kb-model")))
                .thenReturn(Mono.error(modelMissing()));

        StepVerifier.create(service.embed(List.of(chunk(1L, "hello")), "kb-model")).verifyComplete();

        verifyNoInteractions(embeddingStore);
    }

    @Test
    void skipsEmbeddingWhenSettingHasNoKnowledgeBaseModel() {
        when(aiSettings.spec()).thenReturn(Mono.just(setting(null)));

        StepVerifier.create(service.embed(List.of(chunk(1L, "hello")), null)).verifyComplete();

        verifyNoInteractions(embeddings, embeddingStore);
    }

    @Test
    void skipsEmbeddingWhenSettingIsMissing() {
        when(aiSettings.spec()).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of(chunk(1L, "hello")), null)).verifyComplete();

        verifyNoInteractions(embeddings, embeddingStore);
    }

    @Test
    void propagatesUpstreamEmbeddingErrors() {
        when(embeddings.embed(anyList(), eq("kb-model")))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, "ai-embedding-upstream-error", "嵌入服务调用失败。")));

        StepVerifier.create(service.embed(List.of(chunk(1L, "hello")), "kb-model"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-embedding-upstream-error");
                })
                .verify();

        verifyNoInteractions(embeddingStore);
    }

    @Test
    void rejectsChunksWithoutAnId() {
        StepVerifier.create(service.embed(List.of(chunk(null, "hello")), "kb-model"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalStateException.class);
                    assertThat(error.getMessage()).contains("ID");
                })
                .verify();

        verifyNoInteractions(embeddings, embeddingStore);
    }

    @Test
    void skipsChunksWithBlankContent() {
        var blank = chunk(1L, "   ");
        var content = chunk(2L, "hello");
        var vector = new float[] {1f, 2f};
        when(embeddings.embed(List.of("hello"), "kb-model"))
                .thenReturn(Mono.just(new EmbeddingBatch("text-embedding-3-small", 2, List.of(vector))));
        when(embeddingStore.updateEmbedding(2L, vector)).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of(blank, content), "kb-model")).verifyComplete();

        verify(embeddingStore, never()).updateEmbedding(eq(1L), any());
        verify(embeddingStore).updateEmbedding(2L, vector);
    }

    @Test
    void rejectsVectorCountMismatch() {
        when(embeddings.embed(anyList(), eq("kb-model")))
                .thenReturn(Mono.just(new EmbeddingBatch(
                        "text-embedding-3-small", 2, List.of(new float[] {1f, 2f}))));

        StepVerifier.create(service.embed(List.of(chunk(1L, "first"), chunk(2L, "second")), "kb-model"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalStateException.class);
                    assertThat(error.getMessage()).contains("数量");
                })
                .verify();

        verifyNoInteractions(embeddingStore);
    }

    @Test
    void ignoresEmptyChunkLists() {
        StepVerifier.create(service.embed(List.of(), "kb-model")).verifyComplete();

        verifyNoInteractions(embeddings, embeddingStore);
    }

    private static KnowledgeBaseChunk chunk(Long id, String content) {
        var chunk = new KnowledgeBaseChunk();
        chunk.setId(id);
        chunk.setContent(content);
        return chunk;
    }

    private static AiSetting.Spec setting(String knowledgeBaseModel) {
        var spec = new AiSetting().getSpec();
        spec.getEmbedding().setKnowledgeBaseModel(knowledgeBaseModel);
        return spec;
    }

    private static ApiException modelMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, Embeddings.MODEL_MISSING_CODE, "未配置嵌入模型。");
    }
}
