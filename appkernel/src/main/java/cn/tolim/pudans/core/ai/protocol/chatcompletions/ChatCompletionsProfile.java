package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import io.agentscope.core.formatter.Formatter;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIRequest;
import io.agentscope.extensions.model.openai.dto.OpenAIResponse;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import java.util.Map;

public interface ChatCompletionsProfile extends AiProtocolProfile {

    default Formatter<OpenAIMessage, OpenAIResponse, OpenAIRequest> formatter() {
        return new OpenAIChatFormatter();
    }

    default Map<String, String> additionalHeaders(Map<String, String> headers) {
        return headers;
    }

    default Map<String, Object> additionalBodyParams(Map<String, Object> bodyParams) {
        return bodyParams;
    }

    default Map<String, Object> reasoningBodyParams(boolean reasoningSupported, String reasoning) {
        return Map.of();
    }

    default boolean stream() {
        return true;
    }

    default void customize(OpenAIChatModel.Builder builder) {}
}
