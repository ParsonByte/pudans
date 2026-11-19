package cn.tolim.pudans.core.ai.provider;

public record AiProviderFile(
        String id,
        String object,
        Long bytes,
        Long createdAt,
        String filename,
        String purpose,
        Long expiresAt) {}
