package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiChannel(
        UUID id,
        String workspaceId,
        String provider,
        String protocol,
        String baseUrl,
        String secretRef,
        boolean credentialConfigured,
        Map<String, Object> credentialStatus,
        String status,
        Integer timeoutMs,
        Integer maxConcurrency,
        Integer weight,
        Integer rateLimit,
        Map<String, Object> config,
        Instant createdAt,
        Instant updatedAt) {}
