package cn.tolim.pudans.event;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class  DomainEvent extends ApplicationEvent {

    private final String eventId;

    private final String type;

    private final Instant occurredAt;

    private final String traceFlowId;

    private final Map<String, Object> payload;

    // 统一传递事件标识、发生时间与业务载荷。
    public DomainEvent(
            Object source,
            String eventId,
            String type,
            Instant occurredAt,
            String traceFlowId,
            Map<String, Object> payload) {
        super(source);
        this.eventId = eventId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.traceFlowId = traceFlowId;
        this.payload = payload;
    }
}
