package cn.tolim.pudans.core.ai.protocol.messages;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MessagesProtocol implements AiChatProtocol {

    private static final MessagesProfile STANDARD = new StandardMessagesProfile();

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
        return "/messages";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }

    @Override
    public Model create(AiChatProtocolRequest request, AiProtocolProfile profile) {
        var resolved = AiProtocolProfiles.resolve(profile, MessagesProfile.class, STANDARD);
        var options = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMillis(request.timeoutMs()))
                        .build())
                .additionalHeaders(resolved.additionalHeaders(request.headers()))
                .additionalBodyParams(resolved.additionalBodyParams(request.additionalBodyParams()));
        var maxTokens = resolved.maxOutputTokens(request.maxOutputTokens());
        if (maxTokens != null && maxTokens > 0) {
            options.maxTokens(maxTokens);
        }
        var builder = AnthropicChatModel.builder()
                .baseUrl(request.baseUrl())
                .modelName(request.modelId())
                .stream(resolved.stream())
                .formatter(resolved.formatter())
                .defaultOptions(options.build());
        if (resolved.authentication() == MessagesProfile.Authentication.BEARER) {
            builder.authToken(request.apiKey());
        } else {
            builder.apiKey(request.apiKey());
        }
        if (request.contextWindow() != null && request.contextWindow() > 0) {
            builder.contextWindowSize(request.contextWindow());
        }
        resolved.customize(builder);
        return builder.build();
    }
}
