package cn.tolim.pudans.contract.ai.dto;

import java.util.Map;

public record AiChannelCreateRequest(
        String provider,
        String protocol,
        String baseUrl,
        String secretRef,
        Map<String, String> secretKeys,
        Map<String, String> credentials,
        String status,
        Integer timeoutMs,
        Integer maxConcurrency,
        Integer weight,
        Integer rateLimit,
        Map<String, Object> config) {}
