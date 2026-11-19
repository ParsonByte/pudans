package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.rag.Citation;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AiChatCompletionResult(
        long conversationId,
        String messageId,
        String content,
        String model,
        String provider,
        String account,
        AiTokenUsage usage,
        BigDecimal points,
        List<Citation> citations) {

    public AiChatCompletionResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record StreamMeta(
            String requestId,
            long conversationId,
            @Nullable String mode,
            String routeGroup,
            String provider,
            String account,
            String model,
            @Nullable String tier,
            String teamId,
            String agentId,
            String sessionKey) {}
}
