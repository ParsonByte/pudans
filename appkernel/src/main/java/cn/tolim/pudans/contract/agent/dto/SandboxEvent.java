package cn.tolim.pudans.contract.agent.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record SandboxEvent(
        String eventId,
        long seq,
        UUID sandboxId,
        String type,
        Map<String, Object> data,
        Instant createdAt) {

    public SandboxEvent {
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
