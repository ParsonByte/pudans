package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;

public record AiCompletionChunk(String text, String finishReason, AiTokenUsage usage) {}
