package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.Map;

public interface ChatCompletionsProfile extends AiProtocolProfile {

    default Map<String, String> additionalHeaders(Map<String, String> headers) {
        return headers;
    }

    default Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
        return bodyParams;
    }

    default Map<String, Object> reasoningBodyParams(boolean reasoningSupported, String reasoning) {
        return Map.of();
    }

    default void customize(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder) {}
}