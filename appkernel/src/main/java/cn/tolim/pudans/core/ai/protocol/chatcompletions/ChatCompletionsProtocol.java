package cn.tolim.pudans.core.ai.protocol.chatcompletions;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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
    public StreamingChatModel create(
            AiChatProtocolRequest request, AiProtocolProfile profile) {
        var resolved = AiProtocolProfiles.resolve(profile, ChatCompletionsProfile.class, STANDARD);
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(request.baseUrl())
                .apiKey(request.apiKey())
                .modelName(request.modelId())
                .customHeaders(resolved.additionalHeaders(request.headers()))
                .customParameters(resolved.additionalBodyParams(request.additionalBodyParams()))
                .timeout(Duration.ofMillis(request.timeoutMs()))
                .returnThinking(true);
        resolved.customize(builder);
        return builder.build();
    }
}