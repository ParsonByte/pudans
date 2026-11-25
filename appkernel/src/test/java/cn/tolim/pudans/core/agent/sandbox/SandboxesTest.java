package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.dto.SandboxCreateRequest;
import cn.tolim.pudans.contract.agent.dto.SandboxExecRequest;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.sandbox.SandboxStore;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SandboxesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    private static final UUID SESSION_ID = UUID.randomUUID();

    private static final UUID RUN_ID = UUID.randomUUID();

    private static final String WORKSPACE_ROOT = "/home/agentscope/workspace";

    @Mock
    SandboxStore store;

    @Mock
    AgentSessionRepository sessions;

    @Mock
    AgentRunRepository runs;

    @Mock
    SandboxGateways gateways;

    @Mock
    SandboxLeases leases;

    @Mock
    SandboxExecGuard guard;

    @Mock
    SandboxEvents events;

    @Mock
    SandboxSnapshots snapshots;

    @Mock
    SandboxGateway gateway;

    private Sandboxes sandboxes;

    @BeforeEach
    void setUp() {
        var properties = new SandboxProperties();
        properties.setTemplate("default-template");
        properties.setRegion("cn-shanghai");
        properties.setWorkspaceRoot(WORKSPACE_ROOT);
        properties.setDefaultTtl(Duration.ofMinutes(30));
        sandboxes = new Sandboxes(
                store, sessions, runs, gateways, leases, guard, events, snapshots, properties);
        sandboxes.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAndProvisionsTheProviderSandboxWithTheRequestedRun() {
        var request = new SandboxCreateRequest(
                "agentrun", SESSION_ID, RUN_ID, null, null, 1_200);
        var session = mock(AgentSessionEntity.class);
        var run = mock(AgentRunEntity.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(
                        SESSION_ID, ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(session));
        when(runs.findByIdAndSessionId(RUN_ID, SESSION_ID)).thenReturn(Mono.just(run));
        when(store.findById(any())).thenReturn(Mono.empty());
        configureGateway();
        when(store.insert(any())).thenAnswer(invocation -> {
            var row = invocation.getArgument(0, SandboxStore.SessionRow.class);
            return Mono.just(new SandboxStore.Insertion(row, true));
        });
        when(store.markProvisioning(any(), any()))
                .thenReturn(Mono.just(row(Sandbox.Status.PROVISIONING, "{}")));
        when(gateway.create(any())).thenReturn(Mono.just(new SandboxGateway.Created(
                "provider-sandbox-1",
                "{\"sandboxId\":\"provider-sandbox-1\"}",
                WORKSPACE_ROOT)));
        when(store.markReady(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(row(
                        Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}")));
        when(events.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(sandboxes.create(ALICE, request, "request-1"))
                .assertNext(result -> {
                    assertThat(result.created()).isTrue();
                    assertThat(result.sandbox().status()).isEqualTo(Sandbox.Status.READY);
                })
                .verifyComplete();

        var inserted = ArgumentCaptor.forClass(SandboxStore.SessionRow.class);
        verify(store).insert(inserted.capture());
        assertThat(inserted.getValue().runId()).isEqualTo(RUN_ID);
        assertThat(inserted.getValue().agentSessionId()).isEqualTo(SESSION_ID);

        var command = ArgumentCaptor.forClass(SandboxGateway.CreateCommand.class);
        verify(gateway).create(command.capture());
        assertThat(command.getValue().template()).isEqualTo("default-template");
        assertThat(command.getValue().region()).isEqualTo("cn-shanghai");
    }

    @Test
    void acceptsE2bAndUsesItsConfiguredTemplateAndWorkspaceDefaults() {
        var properties = new SandboxProperties();
        properties.setEnabled(true);
        properties.setProvider("e2b");
        properties.setTemplate("agentrun-template");
        properties.setRegion("cn-shanghai");
        properties.setWorkspaceRoot(WORKSPACE_ROOT);
        properties.setDefaultTtl(Duration.ofMinutes(30));
        properties.getE2b().setApiKey("e2b-key");
        properties.getE2b().setTemplate("pudans-e2b");
        properties.getE2b().setWorkspaceRoot("/home/user");
        var e2bSandboxes = new Sandboxes(
                store, sessions, runs, gateways, leases, guard, events, snapshots, properties);
        e2bSandboxes.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        var request = new SandboxCreateRequest(
                "e2b", SESSION_ID, RUN_ID, null, null, 1_200);
        var session = mock(AgentSessionEntity.class);
        var run = mock(AgentRunEntity.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(
                        SESSION_ID, ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(session));
        when(runs.findByIdAndSessionId(RUN_ID, SESSION_ID)).thenReturn(Mono.just(run));
        when(store.findById(any())).thenReturn(Mono.empty());
        configureGateway("e2b");
        when(store.insert(any())).thenAnswer(invocation -> {
            var row = invocation.getArgument(0, SandboxStore.SessionRow.class);
            return Mono.just(new SandboxStore.Insertion(row, true));
        });
        when(store.markProvisioning(any(), any()))
                .thenReturn(Mono.just(row(Sandbox.Status.PROVISIONING, "{}")));
        when(gateway.create(any())).thenReturn(Mono.just(new SandboxGateway.Created(
                "e2b-sandbox-1",
                "{\"sandboxId\":\"e2b-sandbox-1\"}",
                "/home/user")));
        when(store.markReady(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(row(
                        Sandbox.Status.READY, "{\"sandboxId\":\"e2b-sandbox-1\"}")));
        when(events.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(e2bSandboxes.create(ALICE, request, "request-e2b"))
                .assertNext(result -> assertThat(result.created()).isTrue())
                .verifyComplete();

        var inserted = ArgumentCaptor.forClass(SandboxStore.SessionRow.class);
        verify(store).insert(inserted.capture());
        assertThat(inserted.getValue().provider()).isEqualTo("e2b");
        assertThat(inserted.getValue().template()).isEqualTo("pudans-e2b");
        assertThat(inserted.getValue().region()).isNull();
        assertThat(inserted.getValue().workspaceRoot()).isEqualTo("/home/user");

        var command = ArgumentCaptor.forClass(SandboxGateway.CreateCommand.class);
        verify(gateway).create(command.capture());
        assertThat(command.getValue().template()).isEqualTo("pudans-e2b");
        assertThat(command.getValue().region()).isNull();
    }

    @Test
    void replaysTheStoredDefaultsWhenOptionalFieldsAreOmitted() {
        var existing = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        when(store.findById(any())).thenReturn(Mono.just(existing));
        var request = new SandboxCreateRequest("agentrun", SESSION_ID, null, null, null, null);

        StepVerifier.create(sandboxes.create(ALICE, request, "request-1"))
                .assertNext(result -> {
                    assertThat(result.created()).isFalse();
                    assertThat(result.sandbox().template()).isEqualTo("default-template");
                    assertThat(result.sandbox().region()).isEqualTo("cn-shanghai");
                })
                .verifyComplete();

        verifyNoInteractions(gateway, sessions, runs);
        verify(store, never()).insert(any());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedWithDifferentResolvedValues() {
        when(store.findById(any()))
                .thenReturn(Mono.just(row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}")));
        var request = new SandboxCreateRequest(
                "agentrun", SESSION_ID, null, "other-template", "cn-shanghai", null);

        StepVerifier.create(sandboxes.create(ALICE, request, "request-1"))
                .expectErrorSatisfies(error -> assertApiError(
                        error, "sandbox-idempotency-key-conflict"))
                .verify();

        verifyNoInteractions(gateway, sessions, runs);
    }

    @Test
    void validatesAnInsertRaceAgainstTheWinningRequest() {
        var existing = row(Sandbox.Status.PROVISIONING, "{}");
        var session = session();
        when(store.findById(any())).thenReturn(Mono.empty());
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(
                        SESSION_ID, ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(session));
        configureGateway();
        when(store.insert(any()))
                .thenReturn(Mono.just(new SandboxStore.Insertion(existing, false)));
        var request = new SandboxCreateRequest(
                "agentrun", SESSION_ID, null, "other-template", "cn-shanghai", null);

        StepVerifier.create(sandboxes.create(ALICE, request, "request-1"))
                .expectErrorSatisfies(error -> assertApiError(
                        error, "sandbox-idempotency-key-conflict"))
                .verify();

        verify(gateway, never()).create(any());
    }

    @Test
    void executesWithThePersistedProviderStateWithoutABlockingLookup() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var running = row(Sandbox.Status.RUNNING, ready.providerState());
        var operation = operation(SandboxOperation.Status.RUNNING, null);
        var finished = operation(SandboxOperation.Status.SUCCEEDED, 0);
        var request = new SandboxExecRequest(
                "pwd", ".", 30, SandboxExecRequest.RiskLevel.LOW);
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        configureGateway();
        when(guard.validate(request, WORKSPACE_ROOT))
                .thenReturn(new SandboxExecGuard.Command("pwd", WORKSPACE_ROOT, 30));
        when(leases.acquire(ready.id()))
                .thenReturn(Mono.just(new TokenLease("lock", "token", Duration.ofMinutes(1))));
        when(leases.release(any())).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(store.markRunning(eq(ready.id()), any())).thenReturn(Mono.just(running));
        when(store.insertOperation(eq(ready.id()), eq("exec"), eq("pwd"), eq(30), any()))
                .thenReturn(Mono.just(operation));
        when(events.publish(any(), any(), any())).thenReturn(Mono.empty());
        when(gateway.exec(any())).thenReturn(Mono.just(new SandboxGateway.Execution(
                0, "/home/agentscope/workspace\n", "", false)));
        when(store.finishOperation(
                        eq(operation.id()),
                        eq(SandboxOperation.Status.SUCCEEDED),
                        eq(0),
                        any(),
                        any(),
                        eq(false),
                        any(),
                        any(),
                        any()))
                .thenReturn(Mono.just(finished));

        StepVerifier.create(sandboxes.exec(ALICE, ready.id(), request))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SandboxOperation.Status.SUCCEEDED);
                    assertThat(result.exitCode()).isZero();
                    assertThat(result.stdout()).contains("workspace");
                })
                .verifyComplete();

        var command = ArgumentCaptor.forClass(SandboxGateway.ExecCommand.class);
        verify(gateway).exec(command.capture());
        assertThat(command.getValue().providerState())
                .isEqualTo("{\"sandboxId\":\"provider-sandbox-1\"}");
        verify(store, never()).findById(any());
    }

    @Test
    void deletesAFailedSandboxWithoutInvokingTheProvider() {
        var failed = row(Sandbox.Status.FAILED, "{}");
        var cancelled = row(Sandbox.Status.CANCELLED, "{}");
        when(store.findOwned(failed.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(failed));
        configureGateway();
        when(leases.acquire(failed.id()))
                .thenReturn(Mono.just(new TokenLease("lock", "token", Duration.ofMinutes(1))));
        when(leases.release(any())).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(store.markCancelled(eq(failed.id()), any())).thenReturn(Mono.just(cancelled));
        when(events.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(sandboxes.delete(ALICE, failed.id())).verifyComplete();

        verify(gateway, never()).destroy(any(), any());
        verify(leases).release(any());
    }

    @Test
    void resumesAStoppedSandboxWithItsStoredTemplateAndRegion() {
        var stopped = row(Sandbox.Status.STOPPED, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var resuming = row(Sandbox.Status.RESUMING, stopped.providerState());
        var ready = row(
                stopped.id(),
                Sandbox.Status.READY,
                "{\"sandboxId\":\"provider-sandbox-1\"}");
        when(store.findOwned(stopped.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(stopped));
        configureGateway();
        when(leases.acquire(stopped.id()))
                .thenReturn(Mono.just(new TokenLease("lock", "token", Duration.ofMinutes(1))));
        when(leases.release(any())).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(store.markResuming(eq(stopped.id()), any())).thenReturn(Mono.just(resuming));
        when(gateway.resume(any())).thenReturn(Mono.just(new SandboxGateway.Resumed(
                "provider-sandbox-1",
                "{\"sandboxId\":\"provider-sandbox-1\"}",
                WORKSPACE_ROOT)));
        when(store.markResumedReady(
                        eq(stopped.id()), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(ready));
        when(events.publish(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(sandboxes.resume(ALICE, stopped.id()))
                .assertNext(result -> assertThat(result.status()).isEqualTo(Sandbox.Status.READY))
                .verifyComplete();

        var command = ArgumentCaptor.forClass(SandboxGateway.ResumeCommand.class);
        verify(gateway).resume(command.capture());
        assertThat(command.getValue().template()).isEqualTo("default-template");
        assertThat(command.getValue().region()).isEqualTo("cn-shanghai");
        verify(events).publish(eq(stopped.id()), eq(SandboxEvents.RESUMED), any());
    }

    @Test
    void returnsAnActiveSandboxWithoutStartingAnotherResume() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));

        StepVerifier.create(sandboxes.resume(ALICE, ready.id()))
                .assertNext(result -> assertThat(result.status()).isEqualTo(Sandbox.Status.READY))
                .verifyComplete();

        verifyNoInteractions(gateway, leases);
    }

    @Test
    void rejectsResumeFromAFailedSandbox() {
        var failed = row(Sandbox.Status.FAILED, "{}");
        when(store.findOwned(failed.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(failed));

        StepVerifier.create(sandboxes.resume(ALICE, failed.id()))
                .expectErrorSatisfies(error -> assertApiError(
                        error, "sandbox-resume-not-allowed"))
                .verify();

        verifyNoInteractions(gateway, leases);
    }

    @Test
    void uploadsAFileInsideTheWorkspaceAndRecordsTheOperation() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var operation = operation(SandboxOperation.Status.RUNNING, null);
        var finished = operation(SandboxOperation.Status.SUCCEEDED, null);
        var content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var path = WORKSPACE_ROOT + "/notes/a.txt";
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        when(guard.validateFilePath(WORKSPACE_ROOT, "notes/a.txt")).thenReturn(path);
        when(guard.validateUploadSize(content)).thenReturn(content.length);
        configureGateway();
        when(leases.acquire(ready.id()))
                .thenReturn(Mono.just(new TokenLease("lock", "token", Duration.ofMinutes(1))));
        when(leases.release(any())).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(store.insertOperation(eq(ready.id()), eq("upload"), eq(path), anyInt(), any()))
                .thenReturn(Mono.just(operation));
        when(gateway.upload(any())).thenReturn(Mono.empty());
        when(store.finishOperation(
                        eq(operation.id()),
                        eq(SandboxOperation.Status.SUCCEEDED),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(false),
                        isNull(),
                        isNull(),
                        any()))
                .thenReturn(Mono.just(finished));

        StepVerifier.create(sandboxes.upload(ALICE, ready.id(), "notes/a.txt", content))
                .assertNext(result -> {
                    assertThat(result.path()).isEqualTo(path);
                    assertThat(result.sizeBytes()).isEqualTo(content.length);
                    assertThat(result.sha256()).isEqualTo(Digests.sha256Hex(content));
                })
                .verifyComplete();

        var command = ArgumentCaptor.forClass(SandboxGateway.UploadCommand.class);
        verify(gateway).upload(command.capture());
        assertThat(command.getValue().path()).isEqualTo(path);
        assertThat(command.getValue().sha256()).isEqualTo(Digests.sha256Hex(content));
    }

    @Test
    void rejectsAnUploadOutsideTheWorkspaceWithoutAProviderCall() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        when(guard.validateFilePath(WORKSPACE_ROOT, "../outside.txt"))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "sandbox-path-outside-workspace",
                        "路径必须位于沙箱工作区内。"));

        StepVerifier.create(sandboxes.upload(
                        ALICE,
                        ready.id(),
                        "../outside.txt",
                        "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, "sandbox-path-outside-workspace"))
                .verify();

        verifyNoInteractions(gateway, leases);
    }

    @Test
    void downloadsAFileAndRecordsTheOperation() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var operation = operation(SandboxOperation.Status.RUNNING, null);
        var finished = operation(SandboxOperation.Status.SUCCEEDED, null);
        var content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var path = WORKSPACE_ROOT + "/notes/a.txt";
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        when(guard.validateFilePath(WORKSPACE_ROOT, "notes/a.txt")).thenReturn(path);
        configureGateway();
        when(leases.acquire(ready.id()))
                .thenReturn(Mono.just(new TokenLease("lock", "token", Duration.ofMinutes(1))));
        when(leases.release(any())).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(store.insertOperation(eq(ready.id()), eq("download"), eq(path), anyInt(), any()))
                .thenReturn(Mono.just(operation));
        when(gateway.download(any())).thenReturn(Mono.just(new SandboxGateway.Downloaded(
                content, Digests.sha256Hex(content))));
        when(store.finishOperation(
                        eq(operation.id()),
                        eq(SandboxOperation.Status.SUCCEEDED),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(false),
                        isNull(),
                        isNull(),
                        any()))
                .thenReturn(Mono.just(finished));

        StepVerifier.create(sandboxes.download(ALICE, ready.id(), "notes/a.txt"))
                .assertNext(result -> {
                    assertThat(result.content()).isEqualTo(content);
                    assertThat(result.fileName()).isEqualTo("a.txt");
                    assertThat(result.sha256()).isEqualTo(Digests.sha256Hex(content));
                })
                .verifyComplete();
    }

    @Test
    void listsOperationsWithAStartedAtCursor() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var operation = operation(SandboxOperation.Status.SUCCEEDED, 0);
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        when(store.listOperations(eq(ready.id()), isNull(), eq(1)))
                .thenReturn(Flux.just(operation));

        StepVerifier.create(sandboxes.operations(ALICE, ready.id(), null, 1))
                .assertNext(page -> {
                    assertThat(page.items()).containsExactly(operation);
                    assertThat(page.nextCursor()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void reportsAMissingOperationAsNotFound() {
        var ready = row(Sandbox.Status.READY, "{\"sandboxId\":\"provider-sandbox-1\"}");
        var operationId = UUID.randomUUID();
        when(store.findOwned(ready.id(), ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Mono.just(ready));
        when(store.findOperation(ready.id(), operationId)).thenReturn(Mono.empty());

        StepVerifier.create(sandboxes.operation(ALICE, ready.id(), operationId))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOfSatisfying(ApiException.class, api -> {
                        assertThat(api.getStatusCode().value()).isEqualTo(404);
                        assertThat(api.getCode()).isEqualTo("sandbox-operation-not-found");
                    });
                })
                .verify();
    }

    private void configureGateway() {
        configureGateway("agentrun");
    }

    private void configureGateway(String provider) {
        when(gateways.find(provider)).thenReturn(Optional.of(gateway));
        when(gateway.configured()).thenReturn(true);
        lenient().when(gateway.provider()).thenReturn(provider);
    }

    private static AgentSessionEntity session() {
        var session = mock(AgentSessionEntity.class);
        when(session.getId()).thenReturn(SESSION_ID);
        return session;
    }

    private static SandboxStore.SessionRow row(
            Sandbox.Status status, String providerState) {
        return row(UUID.randomUUID(), status, providerState);
    }

    private static SandboxStore.SessionRow row(
            UUID id, Sandbox.Status status, String providerState) {
        return new SandboxStore.SessionRow(
                id,
                ALICE.ownerId(),
                ALICE.workspaceId(),
                SESSION_ID,
                null,
                "agentrun",
                "default-template",
                "cn-shanghai",
                status,
                WORKSPACE_ROOT,
                "provider-sandbox-1",
                providerState,
                NOW.plusSeconds(1_800),
                NOW,
                NOW,
                status == Sandbox.Status.REQUESTED ? null : NOW,
                status == Sandbox.Status.STOPPED || status == Sandbox.Status.CANCELLED
                        ? NOW
                        : null,
                null,
                null);
    }

    private static SandboxOperation operation(
            SandboxOperation.Status status, Integer exitCode) {
        return new SandboxOperation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "exec",
                "pwd",
                status,
                exitCode,
                null,
                null,
                false,
                null,
                30,
                NOW,
                status == SandboxOperation.Status.RUNNING ? null : NOW.plusSeconds(1),
                null,
                null);
    }

    private static void assertApiError(Throwable error, String code) {
        assertApiError(error, 409, code);
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOfSatisfying(ApiException.class, api -> {
            assertThat(api.getStatusCode().value()).isEqualTo(status);
            assertThat(api.getCode()).isEqualTo(code);
        });
    }
}
