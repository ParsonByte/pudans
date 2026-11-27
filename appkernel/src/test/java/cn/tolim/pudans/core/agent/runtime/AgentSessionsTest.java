package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.AgentSessionCreateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class AgentSessionsTest {

    private static final RequestIdentity OWNER = new RequestIdentity("alice", "workspace-a");

    @Mock
    AgentSessionRepository sessions;

    @Mock
    AgentMessageRepository messages;

    private AgentSessions service;

    @BeforeEach
    void setUp() {
        service = new AgentSessions(sessions, messages);
    }

    @Test
    void createsAnOwnedActiveSession() {
        when(sessions.save(any(AgentSessionEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(OWNER, new AgentSessionCreateRequest("Planning", "default", null))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.ownerId()).isEqualTo("alice");
        assertThat(result.workspaceId()).isEqualTo("workspace-a");
        assertThat(result.status()).isEqualTo("active");
        assertThat(result.title()).isEqualTo("Planning");
    }

    @Test
    void hidesSessionsOwnedByAnotherUser() {
        var sessionId = UUID.randomUUID();
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(sessionId, "alice", "workspace-a"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.view(OWNER, sessionId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-session-not-found")))
                .verify();
    }

    @Test
    void readsMessagesBeforeTheTimeCursor() {
        var session = session();
        var message = message(session.getId(), Instant.parse("2026-11-25T01:00:00Z"));
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(
                        session.getId(), "alice", "workspace-a"))
                .thenReturn(Mono.just(session));
        when(messages.findBySessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        eq(session.getId()), any(OffsetDateTime.class), eq(pageable)))
                .thenReturn(Flux.just(message));

        var result = service
                .messages(OWNER, session.getId(), "2026-11-25T02:00:00Z", 20)
                .block();

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo(message.getId());
    }

    @Test
    void summarizesWithoutMessages() {
        when(sessions.countByOwnerIdAndWorkspaceId("alice", "workspace-a")).thenReturn(Mono.just(2L));
        when(sessions.countByOwnerIdAndWorkspaceIdAndStatus("alice", "workspace-a", "active"))
                .thenReturn(Mono.just(1L));
        when(sessions.findFirstByOwnerIdAndWorkspaceIdOrderByLastMessageAtDesc(
                        "alice", "workspace-a"))
                .thenReturn(Mono.empty());

        var result = service.summary(OWNER).block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.active()).isEqualTo(1);
        assertThat(result.latestMessageAt()).isNull();
    }

    @Test
    void listsEverySessionForAdministrativeInspection() {
        var session = session();
        when(sessions.findAllByOrderByUpdatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 20)))
                .thenReturn(Flux.just(session));

        var result = service.listAll(0).block();

        assertThat(result).isNotNull();
        assertThat(result.limit()).isEqualTo(20);
        assertThat(result.items()).extracting(sessionItem -> sessionItem.id())
                .containsExactly(session.getId());
    }

    @Test
    void summarizesEverySessionForAdministrativeInspection() {
        when(sessions.count()).thenReturn(Mono.just(5L));
        when(sessions.countByStatus("active")).thenReturn(Mono.just(3L));
        when(sessions.findFirstByOrderByLastMessageAtDesc()).thenReturn(Mono.empty());

        var result = service.summaryAll().block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.active()).isEqualTo(3);
        assertThat(result.latestMessageAt()).isNull();
    }

    private static AgentSessionEntity session() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var entity = AgentSessionEntity.create("alice", "workspace-a", "Planning", null, null, now);
        return entity;
    }

    private static AgentMessageEntity message(UUID sessionId, Instant createdAt) {
        var entity = AgentMessageEntity.userMessage(
                sessionId,
                UUID.randomUUID(),
                Map.of("type", "text", "text", "hello"),
                List.of(),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return entity;
    }
}
