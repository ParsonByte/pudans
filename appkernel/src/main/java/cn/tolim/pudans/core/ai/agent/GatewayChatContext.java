package cn.tolim.pudans.core.ai.agent;

import org.jspecify.annotations.Nullable;

public record GatewayChatContext(
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
        @Nullable String modelOverride) {}