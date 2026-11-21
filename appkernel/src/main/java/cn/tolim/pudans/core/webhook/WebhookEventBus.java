package cn.tolim.pudans.core.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class WebhookEventBus {

    private static final int BUFFER_SIZE = 1024;

    private final Sinks.Many<WebhookEvent> sink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);

    private final Object emitLock = new Object();

    // 暴露事件流供分发器订阅。
    public Flux<WebhookEvent> events() {
        return sink.asFlux();
    }

    // 发布事件，缓冲区满时记录告警。
    public void emit(WebhookEvent event) {
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(event);
        }
        if (result.isFailure()) {
            log.warn("无法发送 Webhook 事件 [{}]：{}", event.type(), result);
        }
    }

    // 结束事件流，通知订阅者不再有新事件。
    public void complete() {
        sink.tryEmitComplete();
    }
}
