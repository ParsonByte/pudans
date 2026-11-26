package cn.tolim.pudans.core.ai.protocol.messages;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface MessagesProfile extends AiProtocolProfile {

    Set<String> AUTHENTICATION_HEADERS = Set.of("x-api-key", "authorization", "anthropic-version");

    enum Authentication {
        API_KEY,
        BEARER
    }

    default Authentication authentication() {
        return Authentication.API_KEY;
    }

    default Map<String, String> additionalHeaders(Map<String, String> headers) {
        var values = new LinkedHashMap<String, String>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (!AUTHENTICATION_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    values.put(key, value);
                }
            });
        }
        return Map.copyOf(values);
    }

    default Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
        return bodyParams;
    }

    default Map<String, Object> reasoningBodyParams(
            boolean reasoningSupported, String reasoning) {
        return Map.of();
    }

    default Integer maxOutputTokens(Integer requested) {
        return requested;
    }

    default void customize(AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder) {}
}
