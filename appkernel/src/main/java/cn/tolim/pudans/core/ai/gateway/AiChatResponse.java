package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;

public record AiChatResponse(
        String id,
        String model,
        AiChatMessage message,
        AiTokenUsage usage,
        String finishReason) {}
