package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.MemoryCorrectionRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryWithdrawRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryDedupDecisionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PersonalMemoriesTest {

    private static final String OWNER = "owner-a";

    private static final String WORKSPACE = "workspace-a";

    @Mock
    MemoryPersonalRepository personal;

    @Mock
    MemoryDedupDecisionRepository decisions;

    @Mock
    MemoryPoolSourceSupport poolSupport;

    @Mock
    MemoryEvents events;

    private PersonalMemories memories;

    @BeforeEach
    void setUp() {
        memories = new PersonalMemories(personal, decisions, poolSupport, events);
    }

    @Test
    void rejectsViewOutsideTheAuthenticatedOwnerAndWorkspace() {
        var id = UUID.randomUUID();
        when(personal.findOwned(id, OWNER, WORKSPACE)).thenReturn(Mono.empty());

        StepVerifier.create(memories.view(identity(), id))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("memory-not-found");
                })
                .verify();
    }

    @Test
    void correctsBySupersedingTheOldVersionAndPreservingLineage() {
        var id = UUID.randomUUID();
        var current = memory(id, "旧偏好", "preference-theme");
        when(personal.findOwned(id, OWNER, WORKSPACE)).thenReturn(Mono.just(current));
        when(personal.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(personal.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(decisions.insert(
                        any(),
                        any(),
                        any(),
                        eq("refines"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(Mono.empty());
        when(events.publish(
                        eq(identity()),
                        eq(MemoryEvents.UPDATED),
                        any(),
                        eq("L1"),
                        eq("active"),
                        any()))
                .thenReturn(Mono.empty());

        var request = new MemoryCorrectionRequest(
                "preference",
                "preference-theme",
                "新偏好",
                "修正后的偏好",
                List.of(),
                List.of(),
                new BigDecimal("0.9"),
                null,
                null,
                "偏好变化");

        StepVerifier.create(memories.correct(identity(), id, request))
                .assertNext(item -> {
                    assertThat(item.id()).isNotEqualTo(id);
                    assertThat(item.content()).isEqualTo("新偏好");
                    assertThat(item.status()).isEqualTo("active");
                    assertThat(item.canonicalId()).isEqualTo(id);
                    assertThat(item.supersededBy()).isNull();
                    assertThat(item.evidence())
                            .anySatisfy(entry -> assertThat(entry)
                                    .containsEntry("type", "correction")
                                    .containsEntry("previousMemoryId", id.toString()));
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(MemoryPersonalEntity.class);
        verify(personal).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("superseded");
        assertThat(captor.getValue().getValidTo()).isNotNull();
        assertThat(captor.getValue().getSupersededBy()).isNotNull();
    }

    @Test
    void withdrawsWithoutDeletingEvidenceAndRefreshesPoolSupport() {
        var id = UUID.randomUUID();
        var current = memory(id, "需要撤回的事实", "withdraw-fact");
        when(personal.findOwned(id, OWNER, WORKSPACE)).thenReturn(Mono.just(current));
        when(personal.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(poolSupport.afterSourcesChanged(List.of(id.toString()))).thenReturn(Mono.empty());
        when(events.publish(
                        eq(identity()),
                        eq(MemoryEvents.UPDATED),
                        eq(id),
                        eq("L1"),
                        eq("rejected"),
                        any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(memories.withdraw(
                        identity(), id, new MemoryWithdrawRequest("用户撤回")))
                .assertNext(item -> {
                    assertThat(item.status()).isEqualTo("rejected");
                    assertThat(item.validTo()).isNotNull();
                    assertThat(item.evidence())
                            .anySatisfy(entry -> assertThat(entry)
                                    .containsEntry("type", "withdrawal")
                                    .containsEntry("reason", "用户撤回"));
                })
                .verifyComplete();

        verify(personal, never()).insert(any());
        verify(poolSupport).afterSourcesChanged(List.of(id.toString()));
    }

    @Test
    void mergesDuplicateExtractionAndPublishesAnUpdatedEvent() {
        var id = UUID.randomUUID();
        var content = "已知的部署约束";
        var existing = memory(id, content, "deployment-constraint");
        when(personal.findActiveByHash(
                        OWNER, WORKSPACE, "L1", MemoryText.contentHash(content)))
                .thenReturn(Mono.just(existing));
        when(personal.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(decisions.insert(
                        any(),
                        any(),
                        eq(id),
                        eq("duplicate"),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(Mono.empty());
        when(events.publish(
                        eq(identity()),
                        eq(MemoryEvents.UPDATED),
                        eq(id),
                        eq("L1"),
                        eq("active"),
                        any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(memories.extract(
                        identity(),
                        new PersonalMemories.ExtractedMemory(
                                "L1",
                                "fact",
                                "deployment-constraint",
                                content,
                                "部署约束",
                                List.of(Map.of("type", "agent_message")),
                                List.of(UUID.randomUUID().toString()),
                                new BigDecimal("0.9"))))
                .assertNext(item -> {
                    assertThat(item.id()).isEqualTo(id);
                    assertThat(item.content()).isEqualTo(content);
                    assertThat(item.status()).isEqualTo("active");
                })
                .verifyComplete();

        verify(personal, never()).insert(any());
        verify(events).publish(
                eq(identity()),
                eq(MemoryEvents.UPDATED),
                eq(id),
                eq("L1"),
                eq("active"),
                any());
    }

    private static RequestIdentity identity() {
        return new RequestIdentity(OWNER, WORKSPACE);
    }

    private static MemoryPersonalEntity memory(UUID id, String content, String canonicalKey) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPersonalEntity();
        entity.setId(id);
        entity.setOwnerId(OWNER);
        entity.setWorkspaceId(WORKSPACE);
        entity.setLayer("L1");
        entity.setMemoryType("fact");
        entity.setCanonicalKey(canonicalKey);
        entity.setContent(content);
        entity.setSummary(content);
        entity.setEvidence(List.of(Map.of("type", "agent_message")));
        entity.setSourceIds(List.of(UUID.randomUUID().toString()));
        entity.setConfidence(new BigDecimal("0.8"));
        entity.setStatus("active");
        entity.setValidFrom(now);
        entity.setContentHash(MemoryText.contentHash(content));
        entity.setSimhash(MemoryText.simhash(content));
        entity.setCanonicalId(id);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
