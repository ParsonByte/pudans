package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
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
        @Nullable Long conversationId) {}
