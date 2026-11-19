package cn.tolim.pudans.core.ai.gateway.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class AiProtocolJson {

    private AiProtocolJson() {}

    static ObjectNode readObject(ObjectMapper mapper, String body, String protocol) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new AiProtocolException(protocol + " 请求体必须是 JSON 对象");
            }
            return (ObjectNode) node;
        } catch (JsonProcessingException e) {
            throw new AiProtocolException("无法解析 " + protocol + " 请求体", e);
        }
    }

    static String write(ObjectMapper mapper, JsonNode node, String protocol) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new AiProtocolException("无法序列化 " + protocol + " 请求体", e);
        }
    }

    static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    static JsonNode value(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value;
    }

    static Double decimal(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    static Integer integer(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : null;
    }

    static boolean bool(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isBoolean() && value.booleanValue();
    }

    static Boolean booleanValue(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }

    static JsonNode cacheControl(JsonNode node) {
        return value(node, "cache_control");
    }

    static void putCacheControl(ObjectNode target, JsonNode cacheControl) {
        if (cacheControl != null) {
            target.set("cache_control", cacheControl);
        }
    }

    static Map<String, JsonNode> extras(ObjectNode root, Set<String> known) {
        var extras = new LinkedHashMap<String, JsonNode>();
        root.fields().forEachRemaining(entry -> {
            if (!known.contains(entry.getKey())) {
                extras.put(entry.getKey(), entry.getValue());
            }
        });
        return extras;
    }

    static void putExtras(ObjectNode root, Map<String, JsonNode> extras) {
        extras.forEach(root::set);
    }
}
