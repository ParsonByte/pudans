package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AiGenerationCommand(
        String userId,
        @Nullable String model,
        @Nullable String routeGroup,
        AiModel.Capability capability,
        List<AiModelContent> inputs,
        Map<String, JsonNode> params,
        @Nullable String idempotencyKey,
        @Nullable String teamId,
        @Nullable String agentId,
        @Nullable String sessionId) {

    public AiGenerationCommand {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (capability == null) {
            throw new IllegalArgumentException("capability 不能为空");
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
