package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiPlan;
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
        @Nullable String sessionId,
        AiPlan.@Nullable BillingScope billingScope) {

    public AiGenerationCommand {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (capability == null) {
            throw new IllegalArgumentException("capability 不能为空");
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        params = params == null ? Map.of() : Map.copyOf(params);
        billingScope = billingScope == null ? AiPlan.BillingScope.USER : billingScope;
    }

    // 未显式指定时按用户套餐计费
    public AiGenerationCommand(
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
        this(
                userId,
                model,
                routeGroup,
                capability,
                inputs,
                params,
                idempotencyKey,
                teamId,
                agentId,
                sessionId,
                AiPlan.BillingScope.USER);
    }
}
