package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.model.ToolChoice;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public interface ResponsesProfile extends AiProtocolProfile {

    default Map<String, String> headers(String apiKey, Map<String, String> requestHeaders) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("content-type", "application/json");
        headers.put("accept", "text/event-stream");
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("authorization", "Bearer " + apiKey);
        }
        if (requestHeaders != null) {
            headers.putAll(requestHeaders);
        }
        return Map.copyOf(headers);
    }

    default Map<String, Object> mediaItem(String mediaType, String mediaData) {
        return null;
    }

    static ApiException unsupportedMedia(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-responses-content-unsupported", message);
    }

    default Set<String> replayedItemTypes() {
        return Set.of("reasoning");
    }

    default Set<String> providerOutputItemTypes() {
        return Set.of("reasoning", "web_search_call");
    }

    default Set<String> unsupportedBodyFields() {
        return Set.of();
    }

    default Object toolChoice(ToolChoice choice) {
        return null;
    }

    default void validateTemperature(Double temperature) {}

    default void validateTopP(Double topP) {}

    default Map<String, Object> reasoning(String effort) {
        return Map.of("effort", effort);
    }

    default boolean supportsParallelToolCalls() {
        return true;
    }

    default Boolean store() {
        return null;
    }

    default void customizeBody(Map<String, Object> body) {}

    default String eventType(JsonNode event) {
        return event.path("type").asText("");
    }
}
