package cn.tolim.pudans.core.ai.gateway.agent;

import cn.tolim.pudans.core.ai.gateway.AiChatRequest;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.util.List;
import java.util.Optional;

public final class ClaudeCodeAgentAdapter implements AiAgentAdapter {

    public static final String SOURCE = "claude-code";

    private static final String SYSTEM_REMINDER_PREFIX = "<system-reminder>";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public AiRequestKind classifyRequest(AiChatRequest request) {
        var messages = request.ir().messages().stream()
                .filter(ClaudeCodeAgentAdapter::isConversationMessage)
                .toList();
        var markerIndex = lastCacheControlIndex(messages);
        if (markerIndex >= 0) {
            // Claude Code 的 fork 请求把缓存标记固定在倒数第二条消息，避免重复写入缓存
            return markerIndex == messages.size() - 2 ? AiRequestKind.FORK : AiRequestKind.MAIN;
        }
        if (request.ir().tools().isEmpty() && isThinkingDisabled(request.ir())) {
            return AiRequestKind.SIDEQUERY;
        }
        return AiRequestKind.MAIN;
    }

    @Override
    public Optional<String> extractUserText(AiChatRequest request) {
        var userMessage = lastUserMessage(request.ir());
        if (userMessage == null) {
            return Optional.empty();
        }
        for (int i = userMessage.parts().size() - 1; i >= 0; i--) {
            if (userMessage.parts().get(i) instanceof AiContentPart.Text text
                    && !text.text().isBlank()
                    && !isSystemReminder(text.text())) {
                return Optional.of(text.text());
            }
        }
        return Optional.empty();
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

    private static boolean isConversationMessage(AiChatMessage message) {
        return "user".equals(message.role()) || "assistant".equals(message.role());
    }

    private static int lastCacheControlIndex(List<AiChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            for (var part : messages.get(i).parts()) {
                if (part.cacheControl() != null) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isThinkingDisabled(AiChatIr ir) {
        var thinking = ir.requestParams().extra().get("thinking");
        if (thinking == null || !thinking.isObject()) {
            return false;
        }
        var type = thinking.get("type");
        return type != null && type.isTextual() && "disabled".equals(type.textValue());
    }

    private static boolean isSystemReminder(String text) {
        return text.stripLeading().startsWith(SYSTEM_REMINDER_PREFIX);
    }
}
