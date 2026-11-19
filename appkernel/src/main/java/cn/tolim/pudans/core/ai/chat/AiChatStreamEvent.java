package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.rag.Citation;
import java.math.BigDecimal;

public sealed interface AiChatStreamEvent {

    record Meta(AiChatCompletionResult.StreamMeta meta) implements AiChatStreamEvent {}

    record Delta(String content, String reasoning) implements AiChatStreamEvent {}

    record CitationEvent(Citation citation) implements AiChatStreamEvent {}

    record Usage(AiTokenUsage usage, BigDecimal points, String billingSource) implements AiChatStreamEvent {}

    record Done(String messageId, String finishReason, boolean partial) implements AiChatStreamEvent {}

    record Error(String code, String message, boolean retryable, boolean partial) implements AiChatStreamEvent {}

    record Ping() implements AiChatStreamEvent {}
}
