package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class AiProviderJsonSupport {

    private AiProviderJsonSupport() {}

    static String write(ObjectMapper mapper, JsonNode node, String protocol) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new AiProtocolException("无法序列化 " + protocol + " 数据", error);
        }
    }

    static String argumentsText(ObjectMapper mapper, JsonNode arguments, String protocol) {
        if (arguments == null || arguments.isNull()) {
            return "{}";
        }
        if (arguments.isTextual()) {
            return arguments.textValue();
        }
        return write(mapper, arguments, protocol);
    }

    static JsonNode parseArguments(ObjectMapper mapper, String arguments) {
        if (arguments == null) {
            return null;
        }
        try {
            return mapper.readTree(arguments);
        } catch (JsonProcessingException error) {
            return mapper.getNodeFactory().textNode(arguments);
        }
    }

    static JsonNode parseArguments(ObjectMapper mapper, JsonNode arguments) {
        if (arguments == null || !arguments.isTextual()) {
            return arguments;
        }
        return parseArguments(mapper, arguments.textValue());
    }

    static ObjectNode objectArguments(ObjectMapper mapper, JsonNode arguments) {
        var parsed = parseArguments(mapper, arguments);
        if (parsed != null && parsed.isObject()) {
            return (ObjectNode) parsed;
        }
        var wrapped = mapper.createObjectNode();
        if (parsed != null && !parsed.isNull()) {
            wrapped.set("arguments", parsed);
        }
        return wrapped;
    }

    static String contentText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        return content.isTextual() ? content.textValue() : content.toString();
    }
}
