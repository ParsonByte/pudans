package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;

public record AiCompletionResult(
        String id, String model, String text, String finishReason, AiTokenUsage usage) {}
