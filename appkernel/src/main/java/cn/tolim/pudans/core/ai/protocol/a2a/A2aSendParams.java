package cn.tolim.pudans.core.ai.protocol.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record A2aSendParams(A2aMessage message, @Nullable String agentId) {

    public static A2aSendParams parse(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw A2aRpcException.invalidParams("params 必须是对象。");
        }
        var messageNode = params.get("message");
        var message = A2aMessage.parse(messageNode);
        var agentId = agentId(params.get("metadata"));
        if (agentId == null) {
            agentId = agentId(messageNode.get("metadata"));
        }
        return new A2aSendParams(message, agentId);
    }

    private static @Nullable String agentId(@Nullable JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            return null;
        }
        var value = metadata.get("agentId");
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText()
                : null;
    }
}