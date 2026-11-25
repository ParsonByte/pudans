package cn.tolim.pudans.core.creation.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GenerationTaskCancellationsTest {

    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    private GenerationTaskCancellations cancellations;

    @BeforeEach
    void setUp() {
        cancellations = new GenerationTaskCancellations(redisTemplate, TTL);
    }

    @Test
    void marksTaskAndRunScopesWithTheConfiguredTtl() {
        var taskId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(GenerationTaskKeys.cancel(taskId)), eq("1"), eq(TTL)))
                .thenReturn(Mono.just(true));
        when(valueOperations.set(eq(GenerationTaskKeys.cancel(runId)), eq("1"), eq(TTL)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cancellations.markTask(taskId)).verifyComplete();
        StepVerifier.create(cancellations.markRun(runId)).verifyComplete();
    }

    @Test
    void readsOnlyTheTaskScopeForDirectTasks() {
        var task = task(UUID.randomUUID(), null);
        when(redisTemplate.hasKey(GenerationTaskKeys.cancel(task.id())))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cancellations.cancelled(task))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void treatsEitherTaskOrRunMarkerAsCancelled() {
        var runId = UUID.randomUUID();
        var task = task(UUID.randomUUID(), runId);
        when(redisTemplate.hasKey(GenerationTaskKeys.cancel(task.id())))
                .thenReturn(Mono.just(false));
        when(redisTemplate.hasKey(GenerationTaskKeys.cancel(runId)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cancellations.cancelled(task))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void remainsActiveWhenNoMarkerExists() {
        var runId = UUID.randomUUID();
        var task = task(UUID.randomUUID(), runId);
        when(redisTemplate.hasKey(any(String.class))).thenReturn(Mono.just(false));

        StepVerifier.create(cancellations.cancelled(task))
                .expectNext(false)
                .verifyComplete();
    }

    private static GenerationTask task(UUID taskId, UUID runId) {
        return new GenerationTask(
                taskId,
                "alice",
                "workspace-a",
                null,
                runId,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                UUID.randomUUID(),
                "prompt",
                Map.of(),
                List.of(),
                GenerationTask.Status.PROVIDER_RUNNING,
                true,
                "provider-task-1",
                List.of(),
                null,
                null,
                null,
                "idempotency",
                Instant.parse("2026-11-25T08:00:00Z"),
                Instant.parse("2026-11-25T08:00:00Z"),
                null);
    }
}
