package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import io.agentscope.extensions.model.openai.compat.minimax.MiniMaxFormatter;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ChatCompletionsProtocol implements AiChatProtocol {

    @Override
    public AiProtocol protocol() {
        return AiProtocol.CHAT_COMPLETIONS;
    }

    @Override
    public String description() {
        return "OpenAI 兼容的消息列表协议，DeepSeek、MiniMax 和 DashScope 百炼均可使用。";
    }

    @Override
    public String requestPath() {
        return "/chat/completions";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }

    @Override
    public Model create(AiChatProtocolRequest request) {
        var builder = OpenAIChatModel.builder()
                .apiKey(request.apiKey())
                .modelName(request.modelId())
                .baseUrl(request.baseUrl())
                .endpointPath(request.path())
                .formatter(formatter(request.providerId()))
                .stream(true)
                .nativeStructuredOutput(request.nativeStructuredOutput())
                .nativeStructuredOutputWithTools(request.nativeStructuredOutputWithTools())
                .generateOptions(GenerateOptions.builder()
                        .executionConfig(ExecutionConfig.builder()
                                .timeout(Duration.ofMillis(request.timeoutMs()))
                                .build())
                        .additionalBodyParams(request.additionalBodyParams())
                        .build());
        if (request.contextWindow() != null && request.contextWindow() > 0) {
            builder.contextWindowSize(request.contextWindow());
        }
        return builder.build();
    }

    private static OpenAIChatFormatter formatter(String providerId) {
        return switch (providerId) {
            case "deepseek" -> new DeepSeekFormatter();
            case "minimax" -> new MiniMaxFormatter();
            default -> new OpenAIChatFormatter();
        };
    }
}
