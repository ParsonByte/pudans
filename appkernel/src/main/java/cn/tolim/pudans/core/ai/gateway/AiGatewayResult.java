package cn.tolim.pudans.core.ai.gateway;

import java.math.BigDecimal;

public record AiGatewayResult(
        AiChatResponse response,
        AiGatewayMeta meta,
        AiTokenUsage usage,
        BigDecimal units,
        String billingSource) {}
