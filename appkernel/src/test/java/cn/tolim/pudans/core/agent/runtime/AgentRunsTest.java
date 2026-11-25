package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.AgentApprovalDecisionRequest;
import cn.tolim.pudans.contract.agent.dto.AgentRunStartRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentRunsTest {

    private static final RequestIdentity OWNER = new RequestIdentity("alice", "workspace-a");

    @Mock
    AgentRunRepository runs;

    @Mock
    AgentMessageRepository messages;

    @Mock
    AgentApprovalRepository approvals;

    @Mock
    AgentSessions sessions;

    @Mock
    ObjectProvider<AgentRuntime> runtimes;

    @Mock
    AgentRunEvents events;

    private AgentRuns service;

    @BeforeEach
    void setUp() {
        service = new AgentRuns(runs, messages, approvals, sessions, runtimes, events);
    }

    @Test
    void rejectsStartWhenNoRuntimeIsConfigured() {
        when(runtimes.getIfAvailable()).thenReturn(null);

        StepVerifier.create(service.start(OWNER, Set.of("agent-user"), new AgentRunStartRequest(
                        UUID.randomUUID(), "hello", null, null, null, null)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-runtime-not-configured")))
                .verify();
    }

    @Test
    void persistsTheUserMessageAndQueuedRunBeforeInvokingRuntime() {
        var session = session();
        var runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        when(runtimes.getIfAvailable()).thenReturn(runtime);
        when(sessions.require(OWNER, session.getId())).thenReturn(Mono.just(session));
        when(messages.save(any(AgentMessageEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(runs.save(any(AgentRunEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(sessions.touch(eq(OWNER), eq(session.getId()), any())).thenReturn(Mono.empty());
        when(runtime.start(any())).thenReturn(Mono.empty());
        when(events.publish(any(), any(), any(), anyMap())).thenReturn(Mono.empty());

        var result = service.start(OWNER, Set.of("agent-user"), new AgentRunStartRequest(
                        session.getId(), "hello", null, "fast", "medium", List.of("generate_media")))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("queued");
        var runCaptor = ArgumentCaptor.forClass(AgentRunEntity.class);
        verify(runs).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getInputMessageId()).isNotNull();
        var messageCaptor = ArgumentCaptor.forClass(AgentMessageEntity.class);
        verify(messages, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().getLast().getRunId()).isNotNull();
        var contextCaptor = ArgumentCaptor.forClass(AgentRunContext.class);
        verify(runtime).start(contextCaptor.capture());
        assertThat(contextCaptor.getValue().roles()).containsExactly("agent-user");
        verify(events).publish(any(), eq(session.getId()), eq(AgentRunEvents.RUN_QUEUED), anyMap());
    }

    @Test
    void cancelsBeforeProviderAcceptance() {
        var session = session();
        var run = AgentRunEntity.queued(
                session.getId(),
                UUID.randomUUID(),
                null,
                null,
                null,
                java.util.List.of(),
                OffsetDateTime.now(ZoneOffset.UTC));
        when(runs.findById(run.getId())).thenReturn(Mono.just(run));
        when(sessions.require(OWNER, session.getId())).thenReturn(Mono.just(session));
        when(runs.save(any(AgentRunEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(events.publish(any(), any(), any(), anyMap())).thenReturn(Mono.empty());

        var result = service.cancel(OWNER, Set.of("agent-user"), run.getId()).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("cancelled");
        assertThat(result.cancelRequested()).isTrue();
        assertThat(result.finishedAt()).isNotNull();
        verify(events).publish(
                eq(run.getId()),
                eq(session.getId()),
                eq(AgentRunEvents.RUN_CANCELLED),
                anyMap());
    }

    @Test
    void approvesAPendingApproval() {
        var runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        var session = session();
        var run = AgentRunEntity.queued(
                session.getId(),
                UUID.randomUUID(),
                null,
                null,
                null,
                java.util.List.of(),
                OffsetDateTime.now(ZoneOffset.UTC));
        var approval = new AgentApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setRunId(run.getId());
        approval.setStatus("pending");
        approval.setRequestJson("{}");
        approval.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(runs.findById(run.getId())).thenReturn(Mono.just(run));
        when(sessions.require(OWNER, session.getId())).thenReturn(Mono.just(session));
        when(approvals.findByIdAndRunId(approval.getId(), run.getId())).thenReturn(Mono.just(approval));
        when(approvals.save(any(AgentApprovalEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(runtimes.getIfAvailable()).thenReturn(runtime);
        when(runtime.resume(any(), eq(approval.getId()), eq(true))).thenReturn(Mono.just(true));

        var result = service
                .decide(
                        OWNER,
                        Set.of("agent-user"),
                        run.getId(),
                        approval.getId(),
                        new AgentApprovalDecisionRequest("approve", Map.of("note", "ok")))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.decidedBy()).isEqualTo("alice");
        var order = org.mockito.Mockito.inOrder(runtime, approvals);
        order.verify(runtime).resume(any(), eq(approval.getId()), eq(true));
        order.verify(approvals).save(approval);
        var contextCaptor = ArgumentCaptor.forClass(AgentRunContext.class);
        verify(runtime).resume(contextCaptor.capture(), eq(approval.getId()), eq(true));
        assertThat(contextCaptor.getValue().roles()).containsExactly("agent-user");
    }

    @Test
    void doesNotPersistApprovalWhenTheRuntimeCannotResume() {
        var runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        var session = session();
        var run = AgentRunEntity.queued(
                session.getId(),
                UUID.randomUUID(),
                null,
                null,
                null,
                java.util.List.of(),
                OffsetDateTime.now(ZoneOffset.UTC));
        var approval = new AgentApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setRunId(run.getId());
        approval.setStatus("pending");
        approval.setRequestJson("{}");
        approval.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(runs.findById(run.getId())).thenReturn(Mono.just(run));
        when(sessions.require(OWNER, session.getId())).thenReturn(Mono.just(session));
        when(approvals.findByIdAndRunId(approval.getId(), run.getId())).thenReturn(Mono.just(approval));
        when(runtimes.getIfAvailable()).thenReturn(runtime);
        when(runtime.resume(any(), eq(approval.getId()), eq(true))).thenReturn(Mono.just(false));

        StepVerifier.create(service.decide(
                        OWNER,
                        Set.of("agent-user"),
                        run.getId(),
                        approval.getId(),
                        new AgentApprovalDecisionRequest("approve", Map.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-approval-resume-unavailable")))
                .verify();

        assertThat(approval.getStatus()).isEqualTo("pending");
        verify(approvals, never()).save(any(AgentApprovalEntity.class));
    }

    private static AgentSessionEntity session() {
        return AgentSessionEntity.create(
                "alice",
                "workspace-a",
                "Planning",
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
