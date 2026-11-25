package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryLineageRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryPoolSourceSupportTest {

    @Mock
    MemoryLineageRepository lineage;

    @Mock
    MemoryPoolRepository pool;

    private MemoryPoolSourceSupport support;

    @BeforeEach
    void setUp() {
        support = new MemoryPoolSourceSupport(lineage, pool);
    }

    @Test
    void supersedesPublishedPoolMemoryWhenSupportFallsBelowThreshold() {
        var published = poolItem("published");
        when(pool.findById(published.getId())).thenReturn(Mono.just(published));
        when(lineage.support(published.getId())).thenReturn(Mono.just(
                new MemoryLineageRepository.MemoryPoolSupport(
                        1, 1, 1, List.of("workspace-a"))));
        when(pool.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(support.refresh(published.getId()))
                .assertNext(item -> {
                    assertThat(item.getStatus()).isEqualTo("superseded");
                    assertThat(item.getSupportCount()).isEqualTo(1);
                    assertThat(item.getSourceOwnerCount()).isEqualTo(1);
                    assertThat(item.getSourceWorkspaceCount()).isEqualTo(1);
                    assertThat(item.getSourceWorkspaceIds()).containsExactly("workspace-a");
                    assertThat(item.getValidTo()).isNotNull();
                })
                .verifyComplete();

        verify(pool).update(published);
    }

    private static MemoryPoolEntity poolItem(String status) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPoolEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope("global");
        entity.setLayer("L4");
        entity.setMemoryType("fact");
        entity.setContent("共享经验");
        entity.setGeneralizedContent("共享经验");
        entity.setSupportCount(2);
        entity.setSourceOwnerCount(2);
        entity.setSourceWorkspaceCount(2);
        entity.setSourceWorkspaceIds(List.of("workspace-a", "workspace-b"));
        entity.setEvidenceSummary(Map.of());
        entity.setSafetyClass("general");
        entity.setConfidence(new BigDecimal("0.8"));
        entity.setStatus(status);
        entity.setVisibility("global");
        entity.setValidFrom(now);
        entity.setContentHash(MemoryText.contentHash(entity.getGeneralizedContent()));
        entity.setSimhash(MemoryText.simhash(entity.getGeneralizedContent()));
        entity.setCanonicalId(entity.getId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
