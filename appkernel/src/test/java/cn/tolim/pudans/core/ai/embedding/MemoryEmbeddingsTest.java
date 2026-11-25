package cn.tolim.pudans.core.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingsTest {

    @Mock
    EmbeddingModel embeddingModel;

    private MemoryEmbeddings embeddings;

    @BeforeEach
    void setUp() {
        embeddings = new MemoryEmbeddings();
    }

    @Test
    void delegatesTextToTheEmbeddingModel() {
        when(embeddingModel.embed("需要向量的文本"))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f, 0.3f})));

        StepVerifier.create(embeddings.embed(space(3), "需要向量的文本"))
                .assertNext(vector -> assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f))
                .verifyComplete();
    }

    @Test
    void rejectsEmbeddingVectorsWhoseDimensionDoesNotMatchTheRegisteredSpace() {
        when(embeddingModel.embed("需要向量的文本"))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));

        StepVerifier.create(embeddings.embed(space(3), "需要向量的文本"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("memory-vector-dimension-mismatch");
                })
                .verify();
    }

    @Test
    void rejectsSpacesWithoutAnEmbeddingRoute() {
        StepVerifier.create(
                        embeddings.embed(new MemoryEmbeddingModels.Space(null, null, null), "text"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("memory-embedding-model-not-configured");
                })
                .verify();
    }

    private MemoryEmbeddingModels.Space space(int dimension) {
        var space = new MemoryVectorSpaceEntity();
        space.setId(UUID.randomUUID());
        space.setEmbeddingModelId(UUID.randomUUID());
        space.setModelKey("embedding_model");
        space.setTableName("memory_vectors_embedding_model");
        space.setStorageType("vector");
        space.setDimension(dimension);
        space.setMetric("cosine");
        space.setHnswIndexName("idx_memory_vectors_embedding_model_hnsw");
        space.setStatus("active");
        return new MemoryEmbeddingModels.Space(space, embeddingModel, null);
    }
}