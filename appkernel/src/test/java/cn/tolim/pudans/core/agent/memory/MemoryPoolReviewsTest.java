package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.MemoryPoolReviewRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryPoolReviewsTest {

    private static final RequestIdentity IDENTITY = new RequestIdentity("reviewer-a", "workspace-a");

    private static final Set<String> ROLES = Set.of("memory-reviewer");

    @Mock
    MemoryPoolRepository pool;

    @Mock
    MemoryPoolSourceSupport support;

    @Mock
    MemoryAccess access;

    private MemoryPoolReviews reviews;

    @BeforeEach
    void setUp() {
        reviews = new MemoryPoolReviews(pool, support, access);
    }

    @Test
    void approvesOnlyWhenTwoOwnersAndTwoWorkspacesSupportTheCandidate() {
        var candidate = poolItem("candidate", 2, 2);
        when(access.require(IDENTITY, ROLES, "review")).thenReturn(Mono.empty());
        when(pool.findById(candidate.getId())).thenReturn(Mono.just(candidate));
        when(support.refresh(candidate.getId())).thenReturn(Mono.just(candidate));
        when(pool.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var generalized = "通用部署流程先执行健康检查再切换流量";

        StepVerifier.create(reviews.approve(
                        IDENTITY,
                        ROLES,
                        candidate.getId(),
                        new MemoryPoolReviewRequest(null, generalized, "通用部署流程")))
                .assertNext(item -> {
                    assertThat(item.status()).isEqualTo("approved");
                    assertThat(item.generalizedContent()).isEqualTo(generalized);
                    assertThat(item.contentHash())
                            .isEqualTo(MemoryText.contentHash(generalized));
                    assertThat(item.simhash()).isEqualTo(MemoryText.simhash(generalized));
                    assertThat(item.reviewerId()).isEqualTo(IDENTITY.ownerId());
                })
                .verifyComplete();
    }

    @Test
    void blocksApprovalWhenSourcesDoNotMeetTheCrossOwnerThreshold() {
        var candidate = poolItem("candidate", 1, 2);
        when(access.require(IDENTITY, ROLES, "review")).thenReturn(Mono.empty());
        when(pool.findById(candidate.getId())).thenReturn(Mono.just(candidate));
        when(support.refresh(candidate.getId())).thenReturn(Mono.just(candidate));

        StepVerifier.create(reviews.approve(
                        IDENTITY,
                        ROLES,
                        candidate.getId(),
                        new MemoryPoolReviewRequest(null, "通用经验", null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("memory-pool-sources-insufficient");
                })
                .verify();

        verify(pool, never()).update(any());
    }

    private static MemoryPoolEntity poolItem(
            String status, int sourceOwnerCount, int sourceWorkspaceCount) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPoolEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope("global");
        entity.setLayer("L4");
        entity.setMemoryType("fact");
        entity.setTitle("通用部署");
        entity.setContent("通用部署流程先执行健康检查再切换流量");
        entity.setGeneralizedContent("通用部署流程先执行健康检查再切换流量");
        entity.setSupportCount(sourceOwnerCount + sourceWorkspaceCount);
        entity.setSourceOwnerCount(sourceOwnerCount);
        entity.setSourceWorkspaceCount(sourceWorkspaceCount);
        entity.setSourceWorkspaceIds(java.util.List.of("workspace-a", "workspace-b"));
        entity.setEvidenceSummary(Map.of("canonicalKey", "deployment-health-check"));
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
