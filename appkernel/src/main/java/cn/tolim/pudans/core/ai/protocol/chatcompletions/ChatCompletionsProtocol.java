package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ChatCompletionsProtocol implements AiChatProtocol {

    private static final ChatCompletionsProfile STANDARD = new StandardChatCompletionsProfile();

    @Override
    public AiProtocol protocol() {
        return AiProtocol.CHAT_COMPLETIONS;
    }

    @Override
    public String description() {
        return "OpenAI 兼容的 Chat Completions 线协议，适用于声明兼容该协议的任意渠道。";
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
    public Model create(AiChatProtocolRequest request, AiProtocolProfile profile) {
        var resolved = AiProtocolProfiles.resolve(profile, ChatCompletionsProfile.class, STANDARD);
        var options = GenerateOptions.builder()
                .executionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMillis(request.timeoutMs()))
                        .build())
                .additionalHeaders(resolved.additionalHeaders(request.headers()))
                .additionalBodyParams(resolved.additionalBodyParams(request.additionalBodyParams()))
                .build();
        var builder = OpenAIChatModel.builder()
                .apiKey(request.apiKey())
                .modelName(request.modelId())
                .baseUrl(request.baseUrl())
                .endpointPath(request.path())
                .formatter(resolved.formatter())
                .stream(resolved.stream())
                .nativeStructuredOutput(request.nativeStructuredOutput())
                .nativeStructuredOutputWithTools(request.nativeStructuredOutputWithTools())
                .generateOptions(options);
        if (request.contextWindow() != null && request.contextWindow() > 0) {
            builder.contextWindowSize(request.contextWindow());
        }
        resolved.customize(builder);
        return builder.build();
    }
}
