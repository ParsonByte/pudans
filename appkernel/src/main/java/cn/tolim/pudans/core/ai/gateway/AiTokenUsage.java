package cn.tolim.pudans.core.ai.gateway;

public record AiTokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens,
        Integer cacheWriteTokens) {}
