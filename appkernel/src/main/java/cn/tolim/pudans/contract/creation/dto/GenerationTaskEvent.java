package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record GenerationTaskEvent(
        String eventId,
        long seq,
        UUID runId,
        UUID sessionId,
        String type,
        Map<String, Object> data,
        Instant createdAt) {

    public GenerationTaskEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (seq < 0) {
            throw new IllegalArgumentException("seq must not be negative");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
