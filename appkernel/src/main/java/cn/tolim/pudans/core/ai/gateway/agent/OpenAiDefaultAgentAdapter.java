package cn.tolim.pudans.core.ai.gateway.agent;

import cn.tolim.pudans.core.ai.gateway.AiChatRequest;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.util.Optional;
import java.util.StringJoiner;

public final class OpenAiDefaultAgentAdapter implements AiAgentAdapter {

    public static final String SOURCE = "openai";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public AiRequestKind classifyRequest(AiChatRequest request) {
        return AiRequestKind.MAIN;
    }

    @Override
    public Optional<String> extractUserText(AiChatRequest request) {
        var userMessage = lastUserMessage(request.ir());
        if (userMessage == null) {
            return Optional.empty();
        }
        var text = new StringJoiner("\n");
        for (var part : userMessage.parts()) {
            if (part instanceof AiContentPart.Text content && !content.text().isBlank()) {
                text.add(content.text());
            }
        }
        var joined = text.toString();
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    private static AiChatMessage lastUserMessage(AiChatIr ir) {
        for (int i = ir.messages().size() - 1; i >= 0; i--) {
            var message = ir.messages().get(i);
            if ("user".equals(message.role())) {
                return message;
            }
        }
        return null;
    }
}
