package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AiProviderIrSupport {

    private AiProviderIrSupport() {}

    static boolean isSystem(String role) {
        return "system".equals(role) || "developer".equals(role);
    }

    static String text(List<AiContentPart> parts) {
        var text = new StringBuilder();
        for (var part : parts) {
            if (part instanceof AiContentPart.Text value) {
                text.append(value.text());
            }
        }
        return text.toString();
    }

    static void appendLine(StringBuilder buffer, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!buffer.isEmpty()) {
            buffer.append('\n');
        }
        buffer.append(value);
    }

    static Map<String, String> toolNames(List<AiChatMessage> messages) {
        var names = new HashMap<String, String>();
        for (var message : messages) {
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.ToolCall call
                        && call.id() != null
                        && call.name() != null) {
                    names.put(call.id(), call.name());
                }
            }
        }
        return names;
    }
}
