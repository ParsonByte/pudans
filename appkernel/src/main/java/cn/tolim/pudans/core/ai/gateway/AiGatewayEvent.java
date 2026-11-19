package cn.tolim.pudans.core.ai.gateway;

import java.math.BigDecimal;

public sealed interface AiGatewayEvent {

    record Meta(AiGatewayMeta meta) implements AiGatewayEvent {}

    record Delta(AiStreamEvent event) implements AiGatewayEvent {}

    record Usage(AiTokenUsage usage, BigDecimal points, String billingSource) implements AiGatewayEvent {}

    record Done(String messageId, String finishReason, boolean partial) implements AiGatewayEvent {}

    record Error(String code, String message, boolean retryable, boolean partial) implements AiGatewayEvent {}
}
