package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AiGenerationResult(
        String requestId,
        String status,
        String modelType,
        String capability,
        @Nullable String provider,
        @Nullable String account,
        @Nullable String model,
        List<AiModelContent> outputs,
        AiModelUsage usage,
        @Nullable BigDecimal points,
        @Nullable String billingSource,
        String billingStatus,
        @Nullable String providerTaskId,
        @Nullable String errorCode,
        @Nullable String errorMessage,
        @Nullable Integer latencyMs,
        Instant createdAt,
        @Nullable Instant completedAt) {

    public AiGenerationResult {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        usage = usage == null ? AiModelUsage.EMPTY : usage;
    }

    public static AiGenerationResult from(ModelRequestRow row) {
        return new AiGenerationResult(
                row.requestId(),
                row.status(),
                row.modelType(),
                row.capability(),
                row.selectedProvider(),
                row.selectedAccount(),
                row.selectedModel(),
                outputs(row.resultPayload()),
                usage(row),
                row.points(),
                row.billingSource(),
                row.billingStatus(),
                row.providerTaskId(),
                row.errorCode(),
                row.errorMessage(),
                row.latencyMs(),
                row.createdAt(),
                row.completedAt());
    }

    private static List<AiModelContent> outputs(@Nullable JsonNode payload) {
        var node = payload == null ? null : payload.get("outputs");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return JsonUtils.jsonToObject(node.toString(), new TypeReference<List<AiModelContent>>() {});
    }

    private static AiModelUsage usage(ModelRequestRow row) {
        return new AiModelUsage(
                row.inputTokens(),
                row.outputTokens(),
                row.cacheTokens(),
                null,
                row.imageCount(),
                row.videoSeconds(),
                row.audioSeconds(),
                row.speechCharacters(),
                row.taskCount(),
                java.util.Map.of());
    }
}
