package cn.tolim.pudans.core.webhook;

import cn.tolim.pudans.core.infra.audit.AuditIdGenerator;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final WebhookEventBus eventBus;

    public void publish(String type, Map<String, Object> data, String traceFlowId) {
        publish(AuditIdGenerator.next(), type, Instant.now(), traceFlowId, data);
    }

    public void publish(
            String id, String type, Instant occurredAt, String traceFlowId, Map<String, Object> data) {
        eventBus.emit(new WebhookEvent(id, type, occurredAt, traceFlowId, data == null ? Map.of() : data));
    }
}
