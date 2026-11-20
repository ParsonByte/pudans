package cn.tolim.pudans.core.ai.protocol.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record A2aJsonRpcRequest(Object id, String method, JsonNode params) {

    private static final String VERSION = "2.0";

    public static A2aJsonRpcRequest parse(String body, ObjectMapper mapper) {
        if (!StringUtils.hasText(body)) {
            throw A2aRpcException.parseError("请求体不能为空。");
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException error) {
            throw A2aRpcException.parseError("请求体不是有效的 JSON。");
        }
        if (root == null || !root.isObject()) {
            throw A2aRpcException.invalidRequest("JSON-RPC 请求必须是对象。", null);
        }
        var id = identifier(root.get("id"));
        if (id == null) {
            throw A2aRpcException.invalidRequest("JSON-RPC id 必须是字符串或数字。", null);
        }
        if (!VERSION.equals(text(root, "jsonrpc"))) {
            throw A2aRpcException.invalidRequest("jsonrpc 必须为 2.0。", id);
        }
        var method = text(root, "method");
        if (!StringUtils.hasText(method)) {
            throw A2aRpcException.invalidRequest("method 不能为空。", id);
        }
        return new A2aJsonRpcRequest(id, method, root.path("params"));
    }

    private static @Nullable Object identifier(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        return node.isNumber() ? node.asDouble() : null;
    }

    private static @Nullable String text(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}