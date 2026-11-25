package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.MemoryRetrieveRequest;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryRetrievalLogRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemorySourceRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class MemoryRetrievalTest {

    private static final RequestIdentity IDENTITY =
            new RequestIdentity("owner-a", "workspace-a");

    private static final String QUERY = "deployment preference";

    @Mock
    MemoryPersonalRepository personal;

    @Mock
    MemoryPoolRepository pool;

    @Mock
    MemorySourceRepository sources;

    @Mock
    MemoryVectorRetrieval vectors;

    @Mock
    MemoryRetrievalLogRepository logs;

    private MemoryRetrieval retrieval;

    @BeforeEach
    void setUp() {
        retrieval = new MemoryRetrieval(personal, pool, sources, vectors, logs);
    }

    @Test
    void returnsAnExplicitEmptyResultAndLogsOnlyTheQueryHash() {
        when(pool.publishedLexical(QUERY, 40)).thenReturn(Flux.empty());
        when(personal.activeLexical(
                        IDENTITY.ownerId(), IDENTITY.workspaceId(), QUERY, 40))
                .thenReturn(Flux.empty());
        when(vectors.search(IDENTITY, QUERY, 40)).thenReturn(Mono.just(search(false)));
        when(pool.publishedByIds(List.of())).thenReturn(Flux.empty());
        when(personal.activeByIds(
                        IDENTITY.ownerId(), IDENTITY.workspaceId(), List.of()))
                .thenReturn(Flux.empty());
        when(logs.insert(
                        any(),
                        eq(IDENTITY.ownerId()),
                        eq(IDENTITY.workspaceId()),
                        eq("memory"),
                        eq(MemoryText.queryHash(QUERY)),
                        eq(List.of()),
                        eq(List.of()),
                        eq(List.of()),
                        anyLong(),
                        any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(retrieval.retrieve(
                        IDENTITY, new MemoryRetrieveRequest(QUERY, null, null, null, false)))
                .assertNext(result -> {
                    assertThat(result.items()).isEmpty();
                    assertThat(result.vectorUsed()).isFalse();
                    assertThat(result.warnings())
                            .containsExactly("memory-embedding-model-not-configured");
                })
                .verifyComplete();

        verify(logs).insert(
                any(),
                eq(IDENTITY.ownerId()),
                eq(IDENTITY.workspaceId()),
                eq("memory"),
                eq(MemoryText.queryHash(QUERY)),
                eq(List.of()),
                eq(List.of()),
                eq(List.of()),
                anyLong(),
                any());
    }

    @Test
    void retrievesGlobalPoolWithoutOwnerOrWorkspaceInputsAndLetsPersonalOverrideWin() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var poolItem = poolItem(now);
        var personalItem = personalItem(now);
        when(pool.publishedLexical(QUERY, 40)).thenReturn(Flux.just(poolItem));
        when(personal.activeLexical(
                        IDENTITY.ownerId(), IDENTITY.workspaceId(), QUERY, 40))
                .thenReturn(Flux.just(personalItem));
        when(vectors.search(IDENTITY, QUERY, 40)).thenReturn(Mono.just(search(false)));
        when(pool.publishedByIds(List.of())).thenReturn(Flux.empty());
        when(personal.activeByIds(
                        IDENTITY.ownerId(), IDENTITY.workspaceId(), List.of()))
                .thenReturn(Flux.empty());
        when(logs.insert(
                        any(),
                        anyString(),
                        anyString(),
                        eq("memory"),
                        eq(MemoryText.queryHash(QUERY)),
                        any(),
                        any(),
                        any(),
                        anyLong(),
                        any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(retrieval.retrieve(
                        IDENTITY, new MemoryRetrieveRequest(QUERY, null, null, null, false)))
                .assertNext(result -> {
                    assertThat(result.items()).hasSize(1);
                    assertThat(result.items().getFirst().namespace()).isEqualTo("personal");
                    assertThat(result.items().getFirst().content())
                            .isEqualTo("personal deployment preference");
                })
                .verifyComplete();

        verify(pool).publishedLexical(QUERY, 40);
        verify(personal).activeLexical(
                IDENTITY.ownerId(), IDENTITY.workspaceId(), QUERY, 40);
    }

    private static MemoryVectorRetrieval.SearchResult search(boolean available) {
        return new MemoryVectorRetrieval.SearchResult(
                List.of(),
                List.of(),
                available,
                available ? null : "memory-embedding-model-not-configured");
    }

    private static MemoryPoolEntity poolItem(Instant now) {
        var entity = new MemoryPoolEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope("global");
        entity.setLayer("L1");
        entity.setMemoryType("preference");
        entity.setContent("pool deployment preference");
        entity.setGeneralizedContent("pool deployment preference");
        entity.setSummary("pool deployment preference");
        entity.setSupportCount(2);
        entity.setSourceOwnerCount(2);
        entity.setSourceWorkspaceCount(2);
        entity.setEvidenceSummary(Map.of());
        entity.setSafetyClass("general");
        entity.setConfidence(new BigDecimal("0.9"));
        entity.setStatus("published");
        entity.setVisibility("global");
        entity.setValidFrom(now);
        entity.setContentHash(MemoryText.contentHash("deployment preference"));
        entity.setSimhash(MemoryText.simhash("deployment preference"));
        entity.setCanonicalId(UUID.randomUUID());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static MemoryPersonalEntity personalItem(Instant now) {
        var entity = new MemoryPersonalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(IDENTITY.ownerId());
        entity.setWorkspaceId(IDENTITY.workspaceId());
        entity.setLayer("L1");
        entity.setMemoryType("preference");
        entity.setContent("personal deployment preference");
        entity.setSummary("personal deployment preference");
        entity.setSourceIds(List.of());
        entity.setConfidence(new BigDecimal("0.95"));
        entity.setStatus("active");
        entity.setValidFrom(now);
        entity.setContentHash(MemoryText.contentHash("deployment preference"));
        entity.setSimhash(MemoryText.simhash("deployment preference"));
        entity.setCanonicalId(UUID.randomUUID());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
