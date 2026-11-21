package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record JournalEventDraft(
        String eventId,
        Instant occurredAt,
        JournalEventType type,
        @Nullable String runId,
        @Nullable String turnId,
        boolean required,
        Object payload) {

    public JournalEventDraft {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("事件标识不能为空。");
        }
        Objects.requireNonNull(occurredAt, "事件发生时间不能为空");
        Objects.requireNonNull(type, "事件类型不能为空");
        Objects.requireNonNull(payload, "事件负载不能为空");
    }

    public static JournalEventDraft of(
            JournalEventType type, @Nullable String runId, Object payload) {
        return new JournalEventDraft(
                UUID.randomUUID().toString(), Instant.now(), type, runId, null, true, payload);
    }

    public String payloadJson() {
        return JsonUtils.objectToJson(payload);
    }
}