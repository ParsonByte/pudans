package cn.tolim.pudans.core.ai.task;

import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.infra.properties.AiProperties;
import cn.tolim.pudans.core.infra.r2dbc.ai.task.AiTaskRepository;
import cn.tolim.pudans.core.infra.setting.InitializationPhase;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskWorker implements SmartLifecycle {

    private static final int MAX_BACKOFF_SECONDS = 300;

    private final AiTaskRepository taskRepository;

    private final AiTaskHandlerRegistry handlerRegistry;

    private final AiProperties properties;

    private final AiMetrics metrics;

    private volatile boolean running;

    private Disposable loop;

    private Scheduler scheduler;

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        var worker = properties.getWorker();
        var concurrency = Math.max(1, worker.getMaxConcurrency());
        var workerId = "ai-worker-" + UUID.randomUUID();
        scheduler = Schedulers.newBoundedElastic(concurrency, 10000, "ai-task-worker", 60, true);
        loop = Flux.interval(Duration.ZERO, worker.getPollInterval())
                .onBackpressureDrop()
                .concatMap(tick -> poll(workerId, worker, concurrency))
                .subscribeOn(scheduler)
                .subscribe(null, error -> log.error("AI 任务工作线程异常退出。", error));
    }

    private Mono<Void> poll(String workerId, AiProperties.Worker worker, int concurrency) {
        if (!running) {
            return Mono.empty();
        }
        var lockSeconds = Math.max(1, (int) worker.getLockTtl().toSeconds());
        return taskRepository
                .releaseExpired()
                .thenMany(taskRepository.claim(workerId, Math.max(1, worker.getBatchSize()), lockSeconds))
                .flatMap(task -> execute(task), concurrency)
                .then();
    }

    Mono<Void> execute(AiTask task) {
        var startedAt = Instant.now();
        var handler = handlerRegistry.find(task.taskType());
        if (handler == null) {
            return taskRepository
                    .markFailed(
                            task.id(),
                            "未注册的 AI 任务类型：" + task.taskType(),
                            nextRunAt(task.retryCount()))
                    .doOnSuccess(updated -> recordFailureMetrics(task, startedAt))
                    .then();
        }
        return handler
                .handle(task)
                .then(Mono.defer(() -> taskRepository.markDone(task.id()).then()))
                .doOnSuccess(done -> recordSuccessMetrics(task, startedAt))
                .onErrorResume(error -> {
                    log.warn("AI 任务执行失败：{}（{}）", task.id(), task.taskType(), error);
                    return taskRepository
                            .markFailed(task.id(), message(error), nextRunAt(task.retryCount()))
                            .doOnSuccess(updated -> recordFailureMetrics(task, startedAt))
                            .then();
                });
    }

    private void recordSuccessMetrics(AiTask task, Instant startedAt) {
        metrics.memoryTask(
                task.taskType(), AiMetrics.STATUS_SUCCESS, Duration.between(startedAt, Instant.now()));
    }

    private void recordFailureMetrics(AiTask task, Instant startedAt) {
        var duration = Duration.between(startedAt, Instant.now());
        var retries = task.retryCount() + 1;
        metrics.memoryTask(task.taskType(), AiMetrics.STATUS_FAILED, duration);
        if (retries >= task.maxRetry()) {
            metrics.memoryTask(task.taskType(), AiMetrics.STATUS_DEAD, duration);
            metrics.taskDead(task.taskType(), retries);
        }
    }

    private static Instant nextRunAt(int retryCount) {
        var exponent = Math.min(Math.max(0, retryCount), 8);
        var seconds = Math.min(MAX_BACKOFF_SECONDS, 1L << exponent);
        return Instant.now().plusSeconds(seconds);
    }

    private static String message(Throwable error) {
        var value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (loop != null) {
            loop.dispose();
        }
        if (scheduler != null) {
            scheduler.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return InitializationPhase.CONTROLLERS.getPhase();
    }
}
