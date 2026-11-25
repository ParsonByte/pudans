package cn.tolim.pudans.core.creation.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
class GenerationTaskWorkerTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final UUID TASK_ID = UUID.randomUUID();

    private static final UUID MODEL_ID = UUID.randomUUID();

    @Mock
    AiTaskQueue queue;

    @Mock
    GenerationTaskLeases leases;

    @Mock
    GenerationTasks tasks;

    @Mock
    GenerationTaskPipeline pipeline;

    @Mock
    GenerationTaskStore store;

    private GenerationTaskWorker worker;

    @BeforeEach
    void setUp() {
        worker = new GenerationTaskWorker(queue, leases, tasks, pipeline, store);
        worker.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acknowledgesTerminalTasksWithoutAcquiringALease() {
        var task = task(GenerationTask.Status.SUCCEEDED);
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(queue.acknowledge("1-0")).thenReturn(Mono.empty());

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue).acknowledge("1-0");
        verifyNoInteractions(leases, pipeline, store);
    }

    @Test
    void deadLettersMessagesWithInvalidTaskIds() {
        var message = new AiTaskQueue.Message("1-0", null);
        when(queue.deadLetter("1-0", null, "invalid-task-id")).thenReturn(Mono.empty());

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue).deadLetter("1-0", null, "invalid-task-id");
        verifyNoInteractions(tasks, leases, pipeline, store);
    }

    @Test
    void deadLettersMessagesForMissingTasks() {
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.empty());
        when(queue.deadLetter("1-0", TASK_ID, "task-not-found")).thenReturn(Mono.empty());

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue).deadLetter("1-0", TASK_ID, "task-not-found");
        verifyNoInteractions(leases, pipeline);
    }

    @Test
    void exhaustsTasksThatReachedTheAttemptLimitBeforeTakingALease() {
        var task = task(GenerationTask.Status.QUEUED);
        var failed = task(GenerationTask.Status.FAILED);
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(store.countAttempts(TASK_ID))
                .thenReturn(Mono.just((long) GenerationTaskKeys.MAX_ATTEMPTS));
        when(pipeline.exhaust(task)).thenReturn(Mono.just(failed));
        when(queue.deadLetter("1-0", TASK_ID, "attempts-exhausted")).thenReturn(Mono.empty());

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue).deadLetter("1-0", TASK_ID, "attempts-exhausted");
        verifyNoInteractions(leases);
    }

    @Test
    void executesUnderAHeartbeatLeaseAndAcknowledgesTheTerminalResult() {
        var task = task(GenerationTask.Status.QUEUED);
        var succeeded = task(GenerationTask.Status.SUCCEEDED);
        var lease = lease();
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(store.countAttempts(TASK_ID)).thenReturn(Mono.just(1L));
        when(leases.acquire(TASK_ID)).thenReturn(Mono.just(lease));
        when(leases.heartbeats(eq(lease), anyString())).thenReturn(Flux.never());
        when(pipeline.process(TASK_ID)).thenReturn(Mono.just(succeeded));
        when(queue.acknowledge("1-0")).thenReturn(Mono.empty());
        when(leases.release(lease)).thenReturn(Mono.just(LeaseResult.APPLIED));

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(pipeline).process(TASK_ID);
        verify(queue).acknowledge("1-0");
        verify(leases).release(lease);
    }

    @Test
    void acknowledgesDuplicatesWhenAnotherWorkerStillOwnsTheHeartbeat() {
        var task = task(GenerationTask.Status.QUEUED);
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(store.countAttempts(TASK_ID)).thenReturn(Mono.just(1L));
        when(leases.acquire(TASK_ID)).thenReturn(Mono.empty());
        when(leases.alive(TASK_ID)).thenReturn(Mono.just(true));
        when(queue.acknowledge("1-0")).thenReturn(Mono.empty());

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue).acknowledge("1-0");
        verifyNoInteractions(pipeline);
        verify(leases, never()).release(any());
    }

    @Test
    void leavesTheMessagePendingWhenNeitherTheLeaseNorHeartbeatExists() {
        var task = task(GenerationTask.Status.QUEUED);
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(store.countAttempts(TASK_ID)).thenReturn(Mono.just(1L));
        when(leases.acquire(TASK_ID)).thenReturn(Mono.empty());
        when(leases.alive(TASK_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue, never()).acknowledge(any());
        verifyNoInteractions(pipeline);
    }

    @Test
    void stopsProcessingWithoutAcknowledgingWhenTheLeaseHeartbeatIsLost() {
        var task = task(GenerationTask.Status.QUEUED);
        var lease = lease();
        var message = message(TASK_ID);
        when(tasks.find(TASK_ID)).thenReturn(Mono.just(task));
        when(store.countAttempts(TASK_ID)).thenReturn(Mono.just(1L));
        when(leases.acquire(TASK_ID)).thenReturn(Mono.just(lease));
        when(leases.heartbeats(eq(lease), anyString())).thenReturn(Flux.just(false));
        when(pipeline.process(TASK_ID)).thenReturn(Mono.never());
        when(leases.release(lease)).thenReturn(Mono.just(LeaseResult.LOST));

        StepVerifier.create(worker.processMessage(message)).verifyComplete();

        verify(queue, never()).acknowledge(any());
        verify(leases).release(lease);
    }

    @Test
    void requeuesStaleTasksWithTheRecoveryReason() {
        var first = task(GenerationTask.Status.QUEUED);
        var second = task(UUID.randomUUID(), GenerationTask.Status.PROVIDER_RUNNING);
        when(store.listRecoverable(NOW.minus(Duration.ofMinutes(1)), 200))
                .thenReturn(Flux.just(first, second));
        when(queue.enqueue(first.id(), "recovery")).thenReturn(Mono.just("1-0"));
        when(queue.enqueue(second.id(), "recovery")).thenReturn(Mono.just("2-0"));

        StepVerifier.create(worker.recoverStale()).verifyComplete();

        verify(queue).enqueue(first.id(), "recovery");
        verify(queue).enqueue(second.id(), "recovery");
    }

    private static AiTaskQueue.Message message(UUID taskId) {
        return new AiTaskQueue.Message("1-0", taskId);
    }

    private static TokenLease lease() {
        return new TokenLease(
                GenerationTaskKeys.lock(TASK_ID), UUID.randomUUID().toString(), Duration.ofSeconds(90));
    }

    private static GenerationTask task(GenerationTask.Status status) {
        return task(TASK_ID, status);
    }

    private static GenerationTask task(UUID id, GenerationTask.Status status) {
        return new GenerationTask(
                id,
                "alice",
                "workspace-a",
                null,
                null,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                MODEL_ID,
                "prompt",
                Map.of(),
                List.of(),
                status,
                status != GenerationTask.Status.QUEUED,
                null,
                List.of(),
                null,
                null,
                null,
                "idempotency",
                NOW,
                status == GenerationTask.Status.QUEUED ? null : NOW,
                status.terminal() ? NOW : null);
    }
}
