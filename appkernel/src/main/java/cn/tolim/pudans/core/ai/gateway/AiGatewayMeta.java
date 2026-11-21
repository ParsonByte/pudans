package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.resource.AiModel;

public record AiGatewayMeta(
        String requestId,
        long conversationId,
        String mode,
        String routeGroup,
        String provider,
        String account,
        String model,
        AiModel.Tier tier,
        String teamId,
        String agentId,
        String sessionKey) {}
