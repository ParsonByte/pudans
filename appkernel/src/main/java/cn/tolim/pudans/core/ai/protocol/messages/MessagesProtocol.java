package cn.tolim.pudans.core.ai.protocol.messages;

import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MessagesProtocol implements AiChatProtocol {

    @Override
    public AiProtocol protocol() {
        return AiProtocol.MESSAGES;
    }

    @Override
    public String description() {
        return "Anthropic Messages 兼容协议，使用官方 Anthropic SDK 支持文本、图片、PDF、思考与工具调用。";
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
    public Model create(AiChatProtocolRequest request) {
        var options = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMillis(request.timeoutMs()))
                        .build())
                .additionalHeaders(additionalHeaders(request.headers()))
                .additionalBodyParams(request.additionalBodyParams());
        if (request.maxOutputTokens() != null && request.maxOutputTokens() > 0) {
            options.maxTokens(request.maxOutputTokens());
        }
        var builder = AnthropicChatModel.builder()
                .baseUrl(request.baseUrl())
                .apiKey(request.apiKey())
                .modelName(request.modelId())
                .stream(true)
                .defaultOptions(options.build());
        if (request.contextWindow() != null && request.contextWindow() > 0) {
            builder.contextWindowSize(request.contextWindow());
        }
        return builder.build();
    }

    private static Map<String, String> additionalHeaders(Map<String, String> headers) {
        var values = new LinkedHashMap<String, String>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (!"x-api-key".equalsIgnoreCase(key)
                        && !"anthropic-version".equalsIgnoreCase(key)) {
                    values.put(key, value);
                }
            });
        }
        return Map.copyOf(values);
    }
}
