package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import org.jspecify.annotations.Nullable;

public record AiGatewayInvocation(
        AiProvider.Protocol protocol,
        AiChatIr ir,
        String userId,
        String agentSource,
        String sessionKey,
        @Nullable String sessionId,
        String requestId,
        String idempotencyKey,
        @Nullable String modeCode,
        @Nullable String routeGroupCode,
        @Nullable String agentName,
        @Nullable Long conversationId,
        @Nullable String modelOverride,
        @Nullable String callerKey,
        AiPlan.@Nullable BillingScope usageScope) {

    public AiGatewayInvocation {
        usageScope = usageScope == null ? AiPlan.BillingScope.USER : usageScope;
    }

    // 默认前台调用按用户套餐计费
    public AiGatewayInvocation(
            AiProvider.Protocol protocol,
            AiChatIr ir,
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            String requestId,
            String idempotencyKey,
            @Nullable String modeCode,
            @Nullable String routeGroupCode,
            @Nullable String agentName,
            @Nullable Long conversationId,
            @Nullable String modelOverride,
            @Nullable String callerKey) {
        this(
                protocol,
                ir,
                userId,
                agentSource,
                sessionKey,
                sessionId,
                requestId,
                idempotencyKey,
                modeCode,
                routeGroupCode,
                agentName,
                conversationId,
                modelOverride,
                callerKey,
                AiPlan.BillingScope.USER);
    }

    public AiGatewayInvocation(
            AiProvider.Protocol protocol,
            AiChatIr ir,
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            String requestId,
            String idempotencyKey,
            @Nullable String modeCode,
            @Nullable String routeGroupCode,
            @Nullable String agentName,
            @Nullable Long conversationId,
            @Nullable String modelOverride) {
        this(
                protocol,
                ir,
                userId,
                agentSource,
                sessionKey,
                sessionId,
                requestId,
                idempotencyKey,
                modeCode,
                routeGroupCode,
                agentName,
                conversationId,
                modelOverride,
                null);
    }

    public AiGatewayInvocation(
            AiProvider.Protocol protocol,
            AiChatIr ir,
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            String requestId,
            String idempotencyKey,
            @Nullable String modeCode,
            @Nullable String routeGroupCode,
            @Nullable String agentName,
            @Nullable Long conversationId) {
        this(
                protocol,
                ir,
                userId,
                agentSource,
                sessionKey,
                sessionId,
                requestId,
                idempotencyKey,
                modeCode,
                routeGroupCode,
                agentName,
                conversationId,
                null);
    }
}
