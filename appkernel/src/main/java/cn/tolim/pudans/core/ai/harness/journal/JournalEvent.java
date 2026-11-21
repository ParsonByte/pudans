package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record JournalEvent(
        String eventId,
        long seq,
        Instant occurredAt,
        JournalEventType type,
        @Nullable String runId,
        @Nullable String turnId,
        boolean required,
        String payloadJson) {

    public JournalEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new JournalException("线程日志事件标识不能为空。");
        }
        if (seq < 1) {
            throw new JournalException("线程日志事件序号必须从 1 开始。");
        }
        Objects.requireNonNull(occurredAt, "线程日志事件发生时间不能为空");
        Objects.requireNonNull(type, "线程日志事件类型不能为空");
        Objects.requireNonNull(payloadJson, "线程日志事件负载不能为空");
        try {
            JsonUtils.jsonToObject(payloadJson, JsonNode.class);
        } catch (RuntimeException error) {
            throw new JournalException("线程日志事件负载不是合法 JSON。", error);
        }
    }

    public <T> T payload(Class<T> type) {
        return JsonUtils.jsonToObject(payloadJson, type);
    }

    public Map<String, Object> data() {
        return JsonUtils.jsonToObject(payloadJson, new TypeReference<Map<String, Object>>() {});
    }
}