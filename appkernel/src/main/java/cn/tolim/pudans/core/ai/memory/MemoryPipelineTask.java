package cn.tolim.pudans.core.ai.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MemoryPipelineTask(
        PipelineTaskType taskType,
        String dedupKey,
        MemoryContext context,
        Map<String, Object> payload,
        Schedule schedule) {

    public MemoryPipelineTask {
        Objects.requireNonNull(taskType, "taskType");
        if (dedupKey != null && dedupKey.isBlank()) {
            throw new IllegalArgumentException("dedupKey 不能为空白");
        }
        Objects.requireNonNull(context, "context");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public MemoryPipelineTask(
            PipelineTaskType taskType, String dedupKey, MemoryContext context, Map<String, Object> payload) {
        this(taskType, dedupKey, context, payload, null);
    }

    public record Schedule(Long conversationId, int priority, Instant nextRunAt) {

        public Schedule {
            if (conversationId != null && conversationId <= 0) {
                throw new IllegalArgumentException("conversationId 必须大于 0");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("priority 不能为负数");
            }
        }
    }
}
