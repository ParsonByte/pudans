package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import java.util.Map;

public interface ResponsesProfile extends AiProtocolProfile {

    default Map<String, String> additionalHeaders(Map<String, String> headers) {
        return headers;
    }

    default void customize(OpenAiResponsesStreamingChatModel.Builder builder) {}
}