package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public final class AiResponseEncoders {

    private final OpenAiChatResponseEncoder openAiChat;

    private final AnthropicMessagesResponseEncoder anthropic;

    private final GeminiGatewayCodec gemini;

    public AiResponseEncoders(ObjectMapper mapper, GeminiGatewayCodec gemini) {
        var requiredMapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.openAiChat = new OpenAiChatResponseEncoder(requiredMapper);
        this.anthropic = new AnthropicMessagesResponseEncoder(requiredMapper);
        this.gemini = Objects.requireNonNull(gemini, "Gemini 编解码器不能为空");
    }

    public AiResponseEncoder require(AiProvider.Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("下游协议不能为空");
        }
        return switch (protocol) {
            case OPENAI_CHAT -> openAiChat;
            case ANTHROPIC -> anthropic;
            case GEMINI -> gemini;
            default -> throw new IllegalArgumentException("不支持的下游协议: " + protocol);
        };
    }
}
