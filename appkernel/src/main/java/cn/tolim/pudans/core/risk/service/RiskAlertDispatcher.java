package cn.tolim.pudans.core.risk.service;

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
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.risk.RiskAlert;
import cn.tolim.pudans.contract.risk.RiskAlertChannel;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAlertDispatcher implements InitializingBean, DisposableBean {

    private static final int BUFFER_SIZE = 1024;

    private static final int CONCURRENCY = 4;

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ExtensionGetter extensionGetter;

    private final Sinks.Many<RiskAlert> sink =
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
                .flatMap(this::dispatchToChannels, CONCURRENCY)
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
                    log.warn("Timed out while draining the risk alert queue");
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

    public void dispatch(RiskAlert alert) {
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(alert);
        }
        if (result.isFailure()) {
            log.warn("Unable to emit the risk alert of scene [{}]: {}", alert.scene(), result);
        }
    }

    private Mono<Void> dispatchToChannels(RiskAlert alert) {
        return extensionGetter
                .getEnabledExtensions(RiskAlertChannel.class)
                .flatMap(channel -> Mono.defer(() -> channel.send(alert))
                        .timeout(TIMEOUT)
                        .onErrorResume(error -> {
                            log.warn(
                                    "Risk alert channel [{}] failed to send the alert of scene [{}]",
                                    channel.getClass().getName(),
                                    alert.scene(),
                                    error);
                            return Mono.empty();
                        }), CONCURRENCY)
                .then()
                .onErrorResume(error -> {
                    log.warn("Unable to resolve the risk alert channels", error);
                    return Mono.empty();
                });
    }
}
