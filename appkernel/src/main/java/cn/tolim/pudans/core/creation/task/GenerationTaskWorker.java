package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationTaskWorker {

    private static final int CONCURRENCY = 4;

    private static final Duration READ_BLOCK = Duration.ofSeconds(2);

    private static final Duration CLAIM_IDLE = Duration.ofMinutes(2);

    private static final Duration RECOVERY_AGE = Duration.ofMinutes(1);

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private static final int RECOVERY_LIMIT = 200;

    private final AiTaskQueue queue;

    private final GenerationTaskLeases leases;

    private final GenerationTasks tasks;

    private final GenerationTaskPipeline pipeline;

    private final GenerationTaskStore store;

    private final AtomicBoolean running = new AtomicBoolean();

    private final String consumerName =
            "pudans-ai-" + UUID.randomUUID().toString().substring(0, 8);

    private Clock clock = Clock.systemUTC();

    private Disposable subscription;

    // 扩展初始化后再启动消费，避免提前访问未注册的资源。
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent ignored) {
        start();
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        subscription = initializeWithRetry()
                .then(Mono.defer(this::consumeLoop))
                .subscribe(
                        ignored -> {},
                        error -> log.error("生成任务 worker 已停止", error));
    }

    // 关闭时停止领取消息，正在处理的订阅随后释放各自租约。
    @PreDestroy
    public void stop() {
        running.set(false);
        if (subscription != null) {
            subscription.dispose();
        }
    }

    Mono<Void> recoverStale() {
        var olderThan = now().minus(RECOVERY_AGE);
        return store
                .listRecoverable(olderThan, RECOVERY_LIMIT)
                .concatMap(task -> queue.enqueue(task.id(), "recovery"))
                .then();
    }

    Mono<Void> processMessage(AiTaskQueue.Message message) {
        if (message.taskId() == null) {
            return queue.deadLetter(
                    message.recordId(), null, "invalid-task-id");
        }
        return tasks
                .find(message.taskId())
                .switchIfEmpty(Mono.defer(() -> queue
                        .deadLetter(message.recordId(), message.taskId(), "task-not-found")
                        .then(Mono.empty())))
                .flatMap(task -> processTask(message, task));
    }

    private Mono<Void> initialize() {
        return queue.ensureGroup().then(recoverStale());
    }

    private Mono<Void> initializeWithRetry() {
        return initialize().onErrorResume(error -> {
            log.warn("生成任务 worker 初始化失败，稍后重试", error);
            return Mono.delay(RETRY_DELAY).then(Mono.defer(() -> running.get()
                    ? initializeWithRetry()
                    : Mono.empty()));
        });
    }

    private Mono<Void> consumeLoop() {
        if (!running.get()) {
            return Mono.empty();
        }
        return consumeBatch()
                .onErrorResume(error -> {
                    log.warn("生成任务队列读取失败", error);
                    return Mono.delay(RETRY_DELAY).then();
                })
                .then(Mono.defer(() -> running.get() ? consumeLoop() : Mono.empty()));
    }

    private Mono<Void> consumeBatch() {
        return Flux.concat(
                        queue.read(consumerName, CONCURRENCY, READ_BLOCK),
                        queue.autoClaim(consumerName, CLAIM_IDLE, CONCURRENCY))
                .distinct(AiTaskQueue.Message::recordId)
                .flatMap(
                        message -> processMessage(message).onErrorResume(error -> {
                            log.warn(
                                    "生成任务消息处理失败：record={}",
                                    message.recordId(),
                                    error);
                            return Mono.empty();
                        }),
                        CONCURRENCY)
                .then();
    }

    private Mono<Void> processTask(
            AiTaskQueue.Message message,
            GenerationTask task) {
        if (task.terminal()) {
            return queue.acknowledge(message.recordId());
        }
        return store
                .countAttempts(task.id())
                .flatMap(count -> count >= GenerationTaskKeys.MAX_ATTEMPTS
                        ? pipeline
                                .exhaust(task)
                                .then(queue.deadLetter(
                                        message.recordId(),
                                        task.id(),
                                        "attempts-exhausted"))
                        : leases
                                .acquire(task.id())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .flatMap(lease -> lease.isPresent()
                                        ? execute(message, task, lease.get())
                                        : leases
                                                .alive(task.id())
                                                .flatMap(alive -> alive
                                                        ? queue.acknowledge(message.recordId())
                                                                .then(Mono.empty())
                                                        : Mono.empty())));
    }

    private Mono<Void> execute(
            AiTaskQueue.Message message,
            GenerationTask task,
            TokenLease lease) {
        var leaseLost = Sinks.<Void>empty();
        var heartbeat = leases
                .heartbeats(lease, consumerName)
                .doOnNext(alive -> {
                    if (!alive) {
                        leaseLost.tryEmitEmpty();
                    }
                })
                .onErrorResume(error -> {
                    log.warn("生成任务心跳失败：task={}", task.id(), error);
                    leaseLost.tryEmitEmpty();
                    return Mono.empty();
                })
                .subscribe();
        return pipeline
                .process(task.id())
                .takeUntilOther(leaseLost.asMono())
                .flatMap(result -> result.terminal()
                        ? queue.acknowledge(message.recordId())
                        : Mono.empty())
                .doFinally(signal -> {
                    heartbeat.dispose();
                    leases.release(lease).subscribe(
                            ignored -> {},
                            error -> log.warn("释放生成任务租约失败：task={}", task.id(), error));
                });
    }

    private Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
