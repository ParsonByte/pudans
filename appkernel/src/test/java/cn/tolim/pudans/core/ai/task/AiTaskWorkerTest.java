package cn.tolim.pudans.core.ai.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.task.AiTaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiTaskWorkerTest {

    @Mock
    AiTaskRepository taskRepository;

    @Mock
    AiTaskHandler handler;

    @Test
    void marksAHandledTaskAsDone() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var worker = worker();
        var task = task("L1_EXTRACT");
        when(handler.handle(task)).thenReturn(Mono.empty());
        when(taskRepository.markDone(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        verify(taskRepository).markDone(1L);
    }

    @Test
    void marksAFailedTaskWithBackoff() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var worker = worker();
        var task = task("L1_EXTRACT");
        when(handler.handle(task)).thenReturn(Mono.error(new IllegalStateException("模型不可用")));
        when(taskRepository.markFailed(eq(1L), eq("模型不可用"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        verify(taskRepository).markFailed(eq(1L), eq("模型不可用"), any(Instant.class));
    }

    @Test
    void marksATaskWithUnknownHandlerAsFailed() {
        var worker = new AiTaskWorker(
                taskRepository, new AiTaskHandlerRegistry(List.of()), new AiProperties(), metrics());
        var task = task("UNKNOWN");
        when(taskRepository.markFailed(eq(1L), contains("未注册"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        verify(taskRepository).markFailed(eq(1L), contains("未注册"), any(Instant.class));
    }

    @Test
    void recordsSuccessfulTaskMetrics() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var registry = new SimpleMeterRegistry();
        var worker = worker(registry);
        var task = task("L1_EXTRACT");
        when(handler.handle(task)).thenReturn(Mono.empty());
        when(taskRepository.markDone(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        assertThat(taskCount(registry, "L1_EXTRACT", "SUCCESS")).isEqualTo(1.0);
        var timer = registry.find("ai.memory.task.duration")
                .tag("task_type", "L1_EXTRACT")
                .tag("status", "SUCCESS")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(registry.find("ai.alerts").counter()).isNull();
    }

    @Test
    void recordsFailedTaskMetricsBeforeRetryLimit() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var registry = new SimpleMeterRegistry();
        var worker = worker(registry);
        var task = task("L1_EXTRACT");
        when(handler.handle(task)).thenReturn(Mono.error(new IllegalStateException("模型不可用")));
        when(taskRepository.markFailed(eq(1L), eq("模型不可用"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        assertThat(taskCount(registry, "L1_EXTRACT", "FAILED")).isEqualTo(1.0);
        assertThat(taskCount(registry, "L1_EXTRACT", "DEAD")).isZero();
        assertThat(registry.find("ai.alerts").counter()).isNull();
    }

    @Test
    void recordsDeadTaskAndAlertAtRetryLimit() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var registry = new SimpleMeterRegistry();
        var worker = worker(registry);
        var task = task("L1_EXTRACT", 2, 3);
        when(handler.handle(task)).thenReturn(Mono.error(new IllegalStateException("模型不可用")));
        when(taskRepository.markFailed(eq(1L), eq("模型不可用"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(worker.execute(task)).verifyComplete();

        assertThat(taskCount(registry, "L1_EXTRACT", "FAILED")).isEqualTo(1.0);
        assertThat(taskCount(registry, "L1_EXTRACT", "DEAD")).isEqualTo(1.0);
        var alert = registry.find("ai.alerts")
                .tag("type", "task_dead")
                .tag("severity", "ERROR")
                .counter();
        assertThat(alert).isNotNull();
        assertThat(alert.count()).isEqualTo(1.0);
    }

    @Test
    void rejectsDuplicateHandlers() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");

        assertThatThrownBy(() -> new AiTaskHandlerRegistry(List.of(handler, handler)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsNoHandlerForAnUnknownType() {
        when(handler.taskType()).thenReturn("L1_EXTRACT");
        var registry = new AiTaskHandlerRegistry(List.of(handler));

        assertThat(registry.find("missing")).isNull();
    }

    private AiTaskWorker worker() {
        return worker(new SimpleMeterRegistry());
    }

    private AiTaskWorker worker(SimpleMeterRegistry registry) {
        return new AiTaskWorker(
                taskRepository,
                new AiTaskHandlerRegistry(List.of(handler)),
                new AiProperties(),
                new AiMetrics(registry));
    }

    private static AiMetrics metrics() {
        return new AiMetrics(new SimpleMeterRegistry());
    }

    private static double taskCount(SimpleMeterRegistry registry, String taskType, String status) {
        var counter = registry.find("ai.memory.tasks")
                .tag("task_type", taskType)
                .tag("status", status)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static AiTask task(String type) {
        return task(type, 0, 3);
    }

    private static AiTask task(String type, int retryCount, int maxRetry) {
        return new AiTask(
                1L,
                type,
                null,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                11L,
                0,
                Map.of(),
                retryCount,
                maxRetry,
                Instant.now(),
                Instant.now(),
                Instant.now());
    }
}
