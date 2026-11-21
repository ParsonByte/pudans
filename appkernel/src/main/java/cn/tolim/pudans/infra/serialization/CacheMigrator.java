package cn.tolim.pudans.infra.serialization;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class CacheMigrator implements DisposableBean {

    private static final int QUEUE_CAPACITY = 8192;

    private static final int CONCURRENCY = 2;

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();

    private final Sinks.Many<MigrationTask> sink =
            Sinks.many().unicast().onBackpressureBuffer(new LinkedBlockingQueue<>(QUEUE_CAPACITY));

    private final Object emitLock = new Object();

    private volatile CountDownLatch drained;

    private Disposable subscription;

    // 启动订阅，按并发上限异步写回旧缓存。
    @PostConstruct
    void start() {
        var latch = new CountDownLatch(1);
        this.drained = latch;
        subscription = sink.asFlux()
                .flatMap(
                        task -> Mono.fromRunnable(() -> migrate(task))
                                .subscribeOn(Schedulers.boundedElastic()),
                        CONCURRENCY)
                .doFinally(signal -> latch.countDown())
                .subscribe();
    }

    // 去重后入队，队列满时保留延迟迁移机会。
    public void schedule(Cache cache, Object key, Object value) {
        if (value == null) {
            return;
        }
        var id = cache.getName() + ':' + key;
        if (!scheduled.add(id)) {
            return;
        }
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(new MigrationTask(id, cache, key, value));
        }
        if (result.isFailure()) {
            scheduled.remove(id);
            log.warn("缓存迁移队列已满，已延迟键 {}（缓存 {}）", key, cache.getName());
        }
    }

    // 写回失败仅记录日志，避免影响读取路径。
    private void migrate(MigrationTask task) {
        try {
            task.cache().put(task.key(), task.value());
        } catch (RuntimeException e) {
            log.warn("无法迁移键 {}（缓存 {}）", task.key(), task.cache().getName(), e);
        } finally {
            scheduled.remove(task.id());
        }
    }

    // 关闭时完成剩余迁移并限时等待排空。
    @Override
    public void destroy() {
        sink.tryEmitComplete();
        var latch = this.drained;
        if (latch != null) {
            try {
                if (!latch.await(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    log.warn("排空缓存迁移队列时超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        var current = subscription;
        if (current != null) {
            current.dispose();
        }
    }

    record MigrationTask(String id, Cache cache, Object key, Object value) {}
}
