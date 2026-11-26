package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ResponsesProtocol implements AiChatProtocol {

    private static final ResponsesProfile STANDARD = new StandardResponsesProfile();

    @Override
    public AiProtocol protocol() {
        return AiProtocol.RESPONSES;
    }

    @Override
    public String description() {
        return "OpenAI Responses 线协议，支持结构化输入、函数工具和响应事件流。";
    }

    @Override
    public String requestPath() {
        return "/responses";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }

    @Override
    public StreamingChatModel create(
            AiChatProtocolRequest request, AiProtocolProfile profile) {
        var resolved = AiProtocolProfiles.resolve(profile, ResponsesProfile.class, STANDARD);
        var builder = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(request.baseUrl())
                .apiKey(request.apiKey())
                .modelName(request.modelId())
                .customHeaders(resolved.additionalHeaders(request.headers()))
                .httpClientBuilder(JdkHttpClient.builder()
                        .readTimeout(Duration.ofMillis(request.timeoutMs())));
        if (request.maxOutputTokens() != null && request.maxOutputTokens() > 0) {
            builder.maxOutputTokens(request.maxOutputTokens());
        }
        var effort = resolved.reasoningEffort(request.reasoningSupported(), request.reasoning());
        if (StringUtils.hasText(effort)) {
            builder.reasoningEffort(effort.trim());
        }
        resolved.customize(builder);
        return builder.build();
    }
}
