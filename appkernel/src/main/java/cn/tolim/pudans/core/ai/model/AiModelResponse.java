package cn.tolim.pudans.core.ai.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record AiModelResponse(
        @Nullable String id,
        @Nullable String modelCode,
        List<AiModelContent> outputs,
        @Nullable AiModelUsage usage,
        @Nullable String finishReason,
        @Nullable String providerTaskId,
        TaskStatus status) {

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public AiModelResponse {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        status = status == null ? TaskStatus.SUCCEEDED : status;
    }

    public boolean pending() {
        return status == TaskStatus.PENDING || status == TaskStatus.RUNNING;
    }
}
