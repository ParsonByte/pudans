package cn.tolim.pudans.core.ai.memory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExtractedMemory(
        String recordId,
        String content,
        MemoryType type,
        int priority,
        String sceneName,
        List<String> sourceMessageIds,
        Map<String, Object> metadata) {

    public ExtractedMemory {
        requireText(recordId, "recordId");
        requireText(content, "content");
        Objects.requireNonNull(type, "type");
        requireText(sceneName, "sceneName");
        if (priority < -1 || priority > 100) {
            throw new IllegalArgumentException("priority 必须在 -1 到 100 之间");
        }
        if (priority < 0 && type != MemoryType.INSTRUCTION) {
            throw new IllegalArgumentException("priority 为 -1 时类型必须是 instruction");
        }
        sourceMessageIds = sourceMessageIds == null ? List.of() : List.copyOf(sourceMessageIds);
        if (sourceMessageIds.isEmpty()) {
            throw new IllegalArgumentException("sourceMessageIds 不能为空");
        }
        if (sourceMessageIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("sourceMessageIds 不能包含空白值");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
