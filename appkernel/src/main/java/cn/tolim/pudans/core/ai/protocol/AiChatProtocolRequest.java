package cn.tolim.pudans.core.ai.protocol;

import java.util.Map;

public record AiChatProtocolRequest(
        String providerId,
        String modelId,
        String apiKey,
        String baseUrl,
        String path,
        Map<String, String> headers,
        int timeoutMs,
        Integer contextWindow,
        Integer maxOutputTokens,
        boolean nativeStructuredOutput,
        boolean nativeStructuredOutputWithTools,
        Map<String, Object> additionalBodyParams) {

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
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        providerId = providerId.trim();
        modelId = modelId.trim();
        baseUrl = AiProtocolUrl.baseUrl(baseUrl.trim());
        path = path.startsWith("/") ? path.trim() : "/" + path.trim();
        apiKey = apiKey.trim();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeoutMs = timeoutMs > 0 ? timeoutMs : 60_000;
        additionalBodyParams = additionalBodyParams == null ? Map.of() : Map.copyOf(additionalBodyParams);
    }

    public String url() {
        return AiProtocolUrl.join(baseUrl, path);
    }
}
