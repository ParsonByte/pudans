package cn.tolim.pudans.core.audit.service;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import cn.tolim.pudans.contract.audit.AuditSink;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSinkDispatcher implements InitializingBean, DisposableBean {

    private static final int BUFFER_SIZE = 4096;

    private static final int CONCURRENCY = 4;

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ExtensionGetter extensionGetter;

    private final Sinks.Many<AuditRecord> sink =
            Sinks.many().unicast().onBackpressureBuffer(new LinkedBlockingQueue<>(BUFFER_SIZE));

    private final Object emitLock = new Object();

    private volatile CountDownLatch drained;

    private Disposable subscription;

    @Override
    public void afterPropertiesSet() {
        var latch = new CountDownLatch(1);
        this.drained = latch;
        subscription = sink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(this::dispatchToSinks, CONCURRENCY)
                .doFinally(signal -> latch.countDown())
                .subscribe();
    }

    @Override
    public void destroy() {
        sink.tryEmitComplete();
        var latch = this.drained;
        if (latch != null) {
            try {
                if (!latch.await(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    log.warn("Timed out while draining the audit sink queue");
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

    public void dispatch(AuditRecord record) {
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(record);
        }
        if (result.isFailure()) {
            log.warn(
                    "Unable to emit audit record [{}] to the audit sinks: {}",
                    record.getMetadata() == null ? null : record.getMetadata().getName(),
                    result);
        }
    }

    private Mono<Void> dispatchToSinks(AuditRecord record) {
        return extensionGetter
                .getEnabledExtensions(AuditSink.class)
                .flatMap(auditSink -> Mono.defer(() -> auditSink.accept(record))
                        .timeout(TIMEOUT)
                        .onErrorResume(error -> {
                            log.warn(
                                    "Audit sink [{}] failed to accept the audit record",
                                    auditSink.getClass().getName(),
                                    error);
                            return Mono.empty();
                        }), CONCURRENCY)
                .then()
                .onErrorResume(error -> {
                    log.warn("Unable to resolve the audit sinks", error);
                    return Mono.empty();
                });
    }
}
