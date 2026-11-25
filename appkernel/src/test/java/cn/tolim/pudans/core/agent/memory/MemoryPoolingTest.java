package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryLineageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryPoolingTest {

    private static final String CONTENT = "部署前先执行健康检查";

    @Mock
    MemoryPersonalRepository personal;

    @Mock
    MemoryPoolRepository pool;

    @Mock
    MemoryLineageRepository lineage;

    @Mock
    MemoryEvents events;

    @Mock
    MemoryAudit audit;

    private MemoryPooling pooling;

    @BeforeEach
    void setUp() {
        pooling = new MemoryPooling(personal, pool, lineage, events, audit);
    }

    @Test
    void createsAndAuditsACandidateOnlyForTwoOwnersAndTwoWorkspaces() {
        var first = personalSource("owner-a", "workspace-a");
        var second = personalSource("owner-b", "workspace-b");
        var contentHash = MemoryText.contentHash(CONTENT);
        when(personal.activeForPooling(1_000)).thenReturn(Flux.just(first, second));
        when(pool.findCurrentByHash(contentHash)).thenReturn(Mono.empty());
        when(pool.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(lineage.add(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(events.publishPoolCandidate(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(pooling.consolidate())
                .assertNext(result -> {
                    assertThat(result.scanned()).isEqualTo(2);
                    assertThat(result.created()).isEqualTo(1);
                    assertThat(result.updated()).isZero();
                    assertThat(result.skipped()).isZero();
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(MemoryPoolEntity.class);
        verify(pool).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("candidate");
        assertThat(captor.getValue().getScope()).isEqualTo("global");
        assertThat(captor.getValue().getLayer()).isEqualTo("L4");
        assertThat(captor.getValue().getSourceOwnerCount()).isEqualTo(2);
        assertThat(captor.getValue().getSourceWorkspaceCount()).isEqualTo(2);
        assertThat(captor.getValue().getSourceWorkspaceIds())
                .containsExactly("workspace-a", "workspace-b");
        assertThat(captor.getValue().getContentHash()).isEqualTo(contentHash);
        verify(audit).background(
                eq("system"),
                eq("MEMORY_POOL_CANDIDATE_CREATED"),
                eq("MemoryPoolItem"),
                eq(captor.getValue().getId()),
                eq(true),
                any());
    }

    private static MemoryPersonalEntity personalSource(String ownerId, String workspaceId) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPersonalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setWorkspaceId(workspaceId);
        entity.setLayer("L1");
        entity.setMemoryType("fact");
        entity.setCanonicalKey("deployment-health-check");
        entity.setContent(CONTENT);
        entity.setSummary(CONTENT);
        entity.setEvidence(List.of(Map.of("type", "agent_message")));
        entity.setSourceIds(List.of(UUID.randomUUID().toString()));
        entity.setConfidence(new BigDecimal("0.8"));
        entity.setStatus("active");
        entity.setValidFrom(now);
        entity.setContentHash(MemoryText.contentHash(CONTENT));
        entity.setSimhash(MemoryText.simhash(CONTENT));
        entity.setCanonicalId(entity.getId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
