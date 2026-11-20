package cn.tolim.pudans.core.ai.protocol.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record A2aMessage(
        String messageId, List<A2aPart> parts, @Nullable String contextId, @Nullable String taskId) {

    public A2aMessage {
        parts = List.copyOf(parts);
    }

    public static A2aMessage parse(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw A2aRpcException.invalidParams("params.message 必须是对象。");
        }
        if (!"user".equals(text(node, "role"))) {
            throw A2aRpcException.invalidParams("params.message.role 必须为 user。");
        }
        var messageId = text(node, "messageId");
        if (!StringUtils.hasText(messageId)) {
            throw A2aRpcException.invalidParams("params.message.messageId 不能为空。");
        }
        var partsNode = node.get("parts");
        if (partsNode == null || !partsNode.isArray() || partsNode.isEmpty()) {
            throw A2aRpcException.invalidParams("params.message.parts 不能为空。");
        }
        var parts = new ArrayList<A2aPart>();
        for (var part : partsNode) {
            if (part.isObject()) {
                parts.add(new A2aPart(text(part, "kind"), text(part, "text")));
            }
        }
        if (parts.stream().noneMatch(A2aPart::hasText)) {
            throw A2aRpcException.invalidParams("params.message.parts 至少需要一个文本片段。");
        }
        return new A2aMessage(messageId, parts, text(node, "contextId"), text(node, "taskId"));
    }

    public String text() {
        return parts.stream()
                .filter(A2aPart::hasText)
                .map(A2aPart::text)
                .collect(Collectors.joining("\n"));
    }

    private static @Nullable String text(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}