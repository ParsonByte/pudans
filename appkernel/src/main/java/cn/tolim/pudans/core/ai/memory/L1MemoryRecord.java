package cn.tolim.pudans.core.ai.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record L1MemoryRecord(
        Long id,
        String recordId,
        MemoryContext context,
        Long conversationId,
        String content,
        MemoryType type,
        int priority,
        String sceneName,
        List<Long> sourceMessageIds,
        Map<String, Object> metadata,
        String dedupHash,
        MemoryScope scope,
        boolean orgCandidate,
        MemoryStatus status,
        int version,
        List<Instant> timestamps) {

    public L1MemoryRecord {
        requireText(recordId, "recordId");
        Objects.requireNonNull(context, "context");
        requireText(content, "content");
        Objects.requireNonNull(type, "type");
        if (priority < -1 || priority > 100) {
            throw new IllegalArgumentException("priority 必须在 -1 到 100 之间");
        }
        requireText(sceneName, "sceneName");
        sourceMessageIds = sourceMessageIds == null ? List.of() : List.copyOf(sourceMessageIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        requireText(dedupHash, "dedupHash");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        if (version < 1) {
            throw new IllegalArgumentException("version 必须大于 0");
        }
        timestamps = timestamps == null ? List.of() : List.copyOf(timestamps);
    }

    public static L1MemoryRecord newRecord(
            String recordId,
            MemoryContext context,
            Long conversationId,
            String content,
            MemoryType type,
            int priority,
            String sceneName,
            List<Long> sourceMessageIds,
            Map<String, Object> metadata,
            String dedupHash,
            MemoryScope scope,
            boolean orgCandidate,
            int version,
            List<Instant> timestamps) {
        return new L1MemoryRecord(
                null,
                recordId,
                context,
                conversationId,
                content,
                type,
                priority,
                sceneName,
                sourceMessageIds,
                metadata,
                dedupHash,
                scope,
                orgCandidate,
                MemoryStatus.ACTIVE,
                version,
                timestamps);
    }

    public L1MemoryRecord withId(Long id) {
        return new L1MemoryRecord(
                id,
                recordId,
                context,
                conversationId,
                content,
                type,
                priority,
                sceneName,
                sourceMessageIds,
                metadata,
                dedupHash,
                scope,
                orgCandidate,
                status,
                version,
                timestamps);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
