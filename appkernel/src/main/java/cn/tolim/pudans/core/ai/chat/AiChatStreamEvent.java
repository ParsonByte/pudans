package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.rag.Citation;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public sealed interface AiChatStreamEvent {

    record Meta(AiChatCompletionResult.StreamMeta meta) implements AiChatStreamEvent {}

    record RunStarted(String runId) implements AiChatStreamEvent {}

    record Delta(String content, String reasoning) implements AiChatStreamEvent {}

    record CitationEvent(Citation citation) implements AiChatStreamEvent {}

    record ToolCall(String id, String name, @Nullable String server, @Nullable JsonNode arguments)
            implements AiChatStreamEvent {}

    record ToolResult(String id, String name, String output, boolean isError, long durationMs)
            implements AiChatStreamEvent {}

    record ToolProgress(String id, String message) implements AiChatStreamEvent {}

    record ToolResultDelta(String id, String delta) implements AiChatStreamEvent {}

    record ToolCompensated(String id, String name, String message) implements AiChatStreamEvent {}

    record DataBlock(String phase, String mediaType, @Nullable String data) implements AiChatStreamEvent {}

    record ToolConfirm(String runId, String toolCallId, String name, String message)
            implements AiChatStreamEvent {}

    record ToolExternal(String runId, String toolCallId, String name, String arguments)
            implements AiChatStreamEvent {}

    record ToolExternalResult(String runId, String toolCallId, String output, boolean error)
            implements AiChatStreamEvent {}

    record Artifact(
            String kind,
            String fileId,
            String name,
            String mimeType,
            @Nullable Long size,
            @Nullable String url,
            @Nullable Integer progress)
            implements AiChatStreamEvent {}

    record Stage(String stage, String detail) implements AiChatStreamEvent {}

    record Usage(
            AiTokenUsage usage,
            // 兼容字段名：值为标准 Token 用量。
            BigDecimal points,
            String billingSource)
            implements AiChatStreamEvent {}

    record Done(String messageId, String finishReason, boolean partial) implements AiChatStreamEvent {}

    record Error(String code, String message, boolean retryable, boolean partial) implements AiChatStreamEvent {}

    record Ping() implements AiChatStreamEvent {}
}
