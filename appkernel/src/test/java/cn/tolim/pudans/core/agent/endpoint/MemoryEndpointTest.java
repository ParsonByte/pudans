package cn.tolim.pudans.core.agent.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.MemoryCreateRequest;
import cn.tolim.pudans.core.agent.memory.MemoryAudit;
import cn.tolim.pudans.core.agent.memory.MemoryPoolReviews;
import cn.tolim.pudans.core.agent.memory.MemoryRetrieval;
import cn.tolim.pudans.core.agent.memory.PersonalMemories;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
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
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MemoryEndpointTest {

    private static final RequestIdentity IDENTITY =
            new RequestIdentity("owner-from-auth", "workspace-from-header");

    @Mock
    PersonalMemories memories;

    @Mock
    MemoryRetrieval retrieval;

    @Mock
    MemoryPoolReviews poolReviews;

    @Mock
    MemoryAudit audit;

    @Mock
    ReactiveRequestIdentity requestIdentity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new MemoryEndpoint(
                memories, retrieval, poolReviews, audit, requestIdentity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void fallsBackToAdminPoolWithdrawalForANonPersonalMemory() {
        var poolItem = poolItem();
        when(requestIdentity.resolve(any())).thenReturn(Mono.just(IDENTITY));
        when(requestIdentity.roles()).thenReturn(Mono.just(java.util.Set.of("memory-admin")));
        when(memories.withdraw(eq(IDENTITY), eq(poolItem.getId()), any()))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "memory-not-found", "记忆不存在。")));
        when(poolReviews.withdraw(
                        eq(IDENTITY), eq(java.util.Set.of("memory-admin")), eq(poolItem.getId())))
                .thenReturn(Mono.just(poolItem.toContract()));
        when(audit.record(
                        any(),
                        eq(IDENTITY),
                        eq("MEMORY_POOL_WITHDRAWN"),
                        eq("MemoryPoolItem"),
                        eq(poolItem.getId()),
                        eq(true),
                        any()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/memories/" + poolItem.getId() + "/-/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("reason", "隐私申诉"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(poolItem.getId().toString())
                .jsonPath("$.status")
                .isEqualTo("superseded");
    }

    @Test
    void derivesOwnerAndWorkspaceFromTheAuthenticatedRequestNotTheBody() {
        var created = memory();
        when(requestIdentity.resolve(any())).thenReturn(Mono.just(IDENTITY));
        when(memories.create(eq(IDENTITY), any())).thenReturn(Mono.just(created.toContract()));
        when(audit.record(
                        any(),
                        eq(IDENTITY),
                        eq("MEMORY_CREATED"),
                        eq("MemoryItem"),
                        eq(created.getId()),
                        eq(true),
                        any()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "layer", "L1",
                        "memoryType", "preference",
                        "canonicalKey", "editor-theme",
                        "content", "用户偏好浅色编辑器主题",
                        "confidence", new BigDecimal("0.9")))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(created.getId().toString())
                .jsonPath("$.ownerId")
                .isEqualTo(IDENTITY.ownerId())
                .jsonPath("$.workspaceId")
                .isEqualTo(IDENTITY.workspaceId());

        var captor = ArgumentCaptor.forClass(MemoryCreateRequest.class);
        verify(memories).create(eq(IDENTITY), captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("用户偏好浅色编辑器主题");
        verify(audit).record(
                any(),
                eq(IDENTITY),
                eq("MEMORY_CREATED"),
                eq("MemoryItem"),
                eq(created.getId()),
                eq(true),
                any());
    }

    private static MemoryPersonalEntity memory() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPersonalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(IDENTITY.ownerId());
        entity.setWorkspaceId(IDENTITY.workspaceId());
        entity.setLayer("L1");
        entity.setMemoryType("preference");
        entity.setCanonicalKey("editor-theme");
        entity.setContent("用户偏好浅色编辑器主题");
        entity.setSummary("浅色编辑器主题");
        entity.setEvidence(List.of(Map.of("type", "agent_message")));
        entity.setSourceIds(List.of());
        entity.setConfidence(new BigDecimal("0.9"));
        entity.setStatus("active");
        entity.setValidFrom(now);
        entity.setContentHash("hash");
        entity.setSimhash(1L);
        entity.setCanonicalId(entity.getId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static MemoryPoolEntity poolItem() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var entity = new MemoryPoolEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope("global");
        entity.setLayer("L4");
        entity.setMemoryType("fact");
        entity.setContent("通用部署约束");
        entity.setGeneralizedContent("通用部署约束");
        entity.setSupportCount(2);
        entity.setSourceOwnerCount(2);
        entity.setSourceWorkspaceCount(2);
        entity.setSourceWorkspaceIds(List.of("workspace-a", "workspace-b"));
        entity.setEvidenceSummary(Map.of());
        entity.setSafetyClass("general");
        entity.setConfidence(new BigDecimal("0.8"));
        entity.setStatus("superseded");
        entity.setVisibility("global");
        entity.setValidFrom(now);
        entity.setValidTo(now);
        entity.setContentHash("pool-hash");
        entity.setSimhash(2L);
        entity.setCanonicalId(entity.getId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
