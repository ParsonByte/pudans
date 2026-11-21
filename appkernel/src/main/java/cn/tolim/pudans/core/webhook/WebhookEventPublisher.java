package cn.tolim.pudans.core.webhook;

import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final WebhookEventBus eventBus;

    // 生成事件编号与时间后发布事件。
    public void publish(String type, Map<String, Object> data, String traceFlowId) {
        publish(AuditIdGenerator.next(), type, Instant.now(), traceFlowId, data);
    }

    // 发布携带完整元数据的事件，数据为空时用空映射。
    public void publish(
            String id, String type, Instant occurredAt, String traceFlowId, Map<String, Object> data) {
        eventBus.emit(new WebhookEvent(id, type, occurredAt, traceFlowId, data == null ? Map.of() : data));
    }
}
