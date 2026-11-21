package cn.tolim.pudans.core.infra.event;

import cn.tolim.pudans.event.DomainEvent;
import cn.tolim.pudans.core.webhook.WebhookEventPublisher;
import cn.tolim.pudans.core.infra.audit.AuditIdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher implements InitializingBean, DisposableBean {

    private static final int BUFFER_SIZE = 8192;

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static final String DROPPED_COUNTER = "pudans.domain.events.dropped";

    private final ApplicationEventPublisher applicationEventPublisher;

    private final WebhookEventPublisher webhookEventPublisher;

    private final MeterRegistry meterRegistry;

    private final Sinks.Many<DomainEvent> sink =
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
                .concatMap(event -> Mono.fromRunnable(() -> dispatch(event)).onErrorResume(error -> {
                    log.warn("无法分发领域事件 [{}]", event.getType(), error);
                    return Mono.empty();
                }))
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
                    log.warn("排空领域事件队列时超时");
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

    public void publish(String type, Map<String, Object> payload, String traceFlowId) {
        var event = new DomainEvent(
                this,
                AuditIdGenerator.next(),
                type,
                Instant.now(),
                traceFlowId,
                payload == null ? Map.of() : new LinkedHashMap<>(payload));
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(event);
        }
        if (result.isFailure()) {
            meterRegistry.counter(DROPPED_COUNTER, "type", type).increment();
            log.error("领域事件队列已满，事件被丢弃 [{}]：{}", type, result);
        }
    }

    private void dispatch(DomainEvent event) {
        webhookEventPublisher.publish(
                event.getEventId(),
                event.getType(),
                event.getOccurredAt(),
                event.getTraceFlowId(),
                event.getPayload());
        applicationEventPublisher.publishEvent(event);
    }
}
