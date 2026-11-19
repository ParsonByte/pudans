package cn.tolim.pudans.core.ai.task;

import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AiTask(
        long id,
        String taskType,
        @Nullable String dedupKey,
        String spaceId,
        @Nullable String userId,
        @Nullable String teamId,
        @Nullable String agentId,
        @Nullable String sessionId,
        @Nullable Long conversationId,
        int priority,
        Map<String, Object> payload,
        int retryCount,
        int maxRetry,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public AiTask {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType 不能为空");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
