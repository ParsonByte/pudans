package cn.tolim.pudans.core.ai.memory.asset;

import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MemoryL1Item(
        Long id,
        String recordId,
        String content,
        MemoryType type,
        MemoryScope scope,
        int priority,
        String sceneName,
        int version,
        MemoryStatus status,
        Instant createdAt,
        Map<String, Object> metadata) {

    public MemoryL1Item {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(type, "type");
        sceneName = sceneName == null ? "" : sceneName;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}