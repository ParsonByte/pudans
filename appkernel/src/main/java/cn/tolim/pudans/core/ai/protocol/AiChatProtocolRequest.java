package cn.tolim.pudans.core.ai.protocol;

import java.util.Map;

public record AiChatProtocolRequest(
        String providerId,
        String modelId,
        String apiKey,
        String baseUrl,
        Map<String, String> headers,
        int timeoutMs,
        Integer maxOutputTokens,
        Map<String, Object> additionalBodyParams,
        String reasoning,
        boolean reasoningSupported) {

    public AiChatProtocolRequest(
            String providerId,
            String modelId,
            String apiKey,
            String baseUrl,
            Map<String, String> headers,
            int timeoutMs,
            Integer maxOutputTokens,
            Map<String, Object> additionalBodyParams) {
        this(
                providerId,
                modelId,
                apiKey,
                baseUrl,
                headers,
                timeoutMs,
                maxOutputTokens,
                additionalBodyParams,
                null,
                false);
    }

    public AiChatProtocolRequest {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId is required");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId is required");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        providerId = providerId.trim();
        modelId = modelId.trim();
        baseUrl = AiProtocolUrl.baseUrl(baseUrl.trim());
        apiKey = apiKey.trim();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeoutMs = timeoutMs > 0 ? timeoutMs : 60_000;
        additionalBodyParams =
                additionalBodyParams == null ? Map.of() : Map.copyOf(additionalBodyParams);
        reasoning = reasoning == null || reasoning.isBlank() ? null : reasoning.trim();
    }
}
