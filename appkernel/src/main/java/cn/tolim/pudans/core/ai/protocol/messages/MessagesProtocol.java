package cn.tolim.pudans.core.ai.protocol.messages;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MessagesProtocol implements AiChatProtocol {

    private static final MessagesProfile STANDARD = new StandardMessagesProfile();

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Override
    public AiProtocol protocol() {
        return AiProtocol.MESSAGES;
    }

    @Override
    public String description() {
        return "Anthropic Messages 线协议，支持文本、图片、PDF、思考与工具调用。";
    }

    @Override
    public String requestPath() {
        return "/v1/messages";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }

    @Override
    public StreamingChatModel create(
            AiChatProtocolRequest request, AiProtocolProfile profile) {
        var resolved = AiProtocolProfiles.resolve(profile, MessagesProfile.class, STANDARD);
        var headers = new LinkedHashMap<>(resolved.additionalHeaders(request.headers()));
        if (resolved.authentication() == MessagesProfile.Authentication.BEARER) {
            headers.put("Authorization", "Bearer " + request.apiKey());
        }
        var bodyParams = new LinkedHashMap<>(
                resolved.additionalBodyParams(request.additionalBodyParams()));
        bodyParams.putAll(
                resolved.reasoningBodyParams(request.reasoningSupported(), request.reasoning()));
        var builder = AnthropicStreamingChatModel.builder()
                .baseUrl(request.baseUrl())
                .apiKey(request.apiKey())
                .version(ANTHROPIC_VERSION)
                .modelName(request.modelId())
                .customHeaders(headers)
                .customParameters(Map.copyOf(bodyParams))
                .timeout(Duration.ofMillis(request.timeoutMs()))
                .returnThinking(true)
                .sendThinking(true);
        var maxTokens = resolved.maxOutputTokens(request.maxOutputTokens());
        builder.maxTokens(maxTokens != null && maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);
        resolved.customize(builder);
        return builder.build();
    }
}
