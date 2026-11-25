package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceCreateRequest;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapability;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapabilityProbe;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryVectorSpacesTest {

    @Mock
    MemoryVectorSpaceRepository spaces;

    @Mock
    AiModelRepository models;

    @Mock
    PgVectorCapabilityProbe capabilityProbe;

    private MemoryVectorSpaces vectorSpaces;

    @BeforeEach
    void setUp() {
        vectorSpaces = new MemoryVectorSpaces(spaces, models, capabilityProbe);
    }

    @Test
    void marksASpaceMissingWhenItsModelDoesNotAdvertiseEmbedding() {
        var space = space();
        var model = model(space.getEmbeddingModelId(), Map.of("chat", Map.of()));
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(true, true, true, "memory-vector-ready")));
        when(spaces.findAll()).thenReturn(Flux.just(space));
        when(models.findById(space.getEmbeddingModelId())).thenReturn(Mono.just(model));
        when(spaces.updateStatus(eq(space.getId()), eq("missing"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(vectorSpaces.sync())
                .assertNext(result -> {
                    assertThat(result.checked()).isEqualTo(1);
                    assertThat(result.available()).isZero();
                    assertThat(result.disabled()).isEqualTo(1);
                    assertThat(result.reasonCode()).isEqualTo("memory-vector-ready");
                })
                .verifyComplete();

        verify(spaces).updateStatus(eq(space.getId()), eq("missing"), any());
        verify(spaces, never()).tableExists(anyString());
    }

    @Test
    void validatesTheRegisteredModelTableColumnAndHnswIndex() {
        var request = request();
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(true, true, true, "memory-vector-ready")));
        when(models.findById(request.embeddingModelId()))
                .thenReturn(Mono.just(model(request.embeddingModelId(), Map.of("embedding", Map.of()))));
        when(models.existsAvailable(request.embeddingModelId(), "workspace-a"))
                .thenReturn(Mono.just(true));
        when(spaces.tableShape(request.tableName()))
                .thenReturn(Mono.just(new MemoryVectorSpaceRepository.TableShape("vector(3)")));
        when(spaces.indexShape(request.tableName(), request.hnswIndexName()))
                .thenReturn(Mono.just(new MemoryVectorSpaceRepository.IndexShape(
                        "hnsw", "embedding", "vector_cosine_ops")));

        StepVerifier.create(vectorSpaces.validate("workspace-a", request))
                .assertNext(result -> {
                    assertThat(result.valid()).isTrue();
                    assertThat(result.reasonCode()).isEqualTo("memory-vector-ready");
                    assertThat(result.actualColumnType()).isEqualTo("vector(3)");
                    assertThat(result.actualIndexMethod()).isEqualTo("hnsw");
                })
                .verifyComplete();
    }

    @Test
    void rejectsAColumnWhoseTypeDoesNotMatchTheRegisteredDimension() {
        var request = request();
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(true, true, true, "memory-vector-ready")));
        when(models.findById(request.embeddingModelId()))
                .thenReturn(Mono.just(model(request.embeddingModelId(), Map.of("embedding", Map.of()))));
        when(models.existsAvailable(request.embeddingModelId(), "workspace-a"))
                .thenReturn(Mono.just(true));
        when(spaces.tableShape(request.tableName()))
                .thenReturn(Mono.just(new MemoryVectorSpaceRepository.TableShape("vector(4)")));

        StepVerifier.create(vectorSpaces.validate("workspace-a", request))
                .assertNext(result -> {
                    assertThat(result.valid()).isFalse();
                    assertThat(result.reasonCode()).isEqualTo("memory-vector-column-mismatch");
                    assertThat(result.expectedColumnType()).isEqualTo("vector(3)");
                    assertThat(result.actualColumnType()).isEqualTo("vector(4)");
                })
                .verifyComplete();
    }

    @Test
    void createsAnActiveSpaceOnlyAfterValidation() {
        var request = request();
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(true, true, true, "memory-vector-ready")));
        when(models.findById(request.embeddingModelId()))
                .thenReturn(Mono.just(model(request.embeddingModelId(), Map.of("embedding", Map.of()))));
        when(models.existsAvailable(request.embeddingModelId(), "workspace-a"))
                .thenReturn(Mono.just(true));
        when(spaces.tableShape(request.tableName()))
                .thenReturn(Mono.just(new MemoryVectorSpaceRepository.TableShape("vector(3)")));
        when(spaces.indexShape(request.tableName(), request.hnswIndexName()))
                .thenReturn(Mono.just(new MemoryVectorSpaceRepository.IndexShape(
                        "hnsw", "embedding", "vector_cosine_ops")));
        when(spaces.insert(any())).thenAnswer(invocation -> {
            var value = invocation.getArgument(0, MemoryVectorSpaceEntity.class);
            return Mono.just(value);
        });

        StepVerifier.create(vectorSpaces.create("workspace-a", request))
                .assertNext(space -> {
                    assertThat(space.status()).isEqualTo("active");
                    assertThat(space.embeddingModelId()).isEqualTo(request.embeddingModelId());
                })
                .verifyComplete();

        verify(spaces).insert(any());
    }

    @Test
    void reportsDisabledSpacesWithoutProbingTheDatabase() {
        var entity = space();
        entity.setStatus("disabled");
        when(spaces.findByModelKey(entity.getModelKey())).thenReturn(Mono.just(entity));

        StepVerifier.create(vectorSpaces.status("workspace-a", entity.getModelKey()))
                .assertNext(result -> {
                    assertThat(result.ready()).isFalse();
                    assertThat(result.reasonCode()).isEqualTo("memory-vector-space-disabled");
                })
                .verifyComplete();

        verifyNoInteractions(capabilityProbe, models);
    }

    private static MemoryVectorSpaceEntity space() {
        var space = new MemoryVectorSpaceEntity();
        space.setId(UUID.randomUUID());
        space.setEmbeddingModelId(UUID.randomUUID());
        space.setModelKey("embedding_model");
        space.setTableName("memory_vectors_embedding_model");
        space.setStorageType("vector");
        space.setDimension(3);
        space.setMetric("cosine");
        space.setHnswIndexName("idx_memory_vectors_embedding_model_hnsw");
        space.setStatus("active");
        space.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        space.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return space;
    }

    private static MemoryVectorSpaceCreateRequest request() {
        return new MemoryVectorSpaceCreateRequest(
                UUID.randomUUID(),
                "embedding_model",
                "memory_vectors_embedding_model",
                "vector",
                3,
                "cosine",
                "idx_memory_vectors_embedding_model_hnsw");
    }

    private static AiModel model(UUID id, Map<String, Object> capabilities) {
        return new AiModel(
                id,
                "logical-model",
                "Logical Model",
                null,
                capabilities,
                8_192,
                2_048,
                Map.of(),
                "active",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
