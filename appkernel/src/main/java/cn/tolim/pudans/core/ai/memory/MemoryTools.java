package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

public final class MemoryTools {

    public static final String SEARCH = "memory_search";

    public static final String ADD = "memory_add";

    private MemoryTools() {}

    public static List<AiTool> definitions() {
        return List.of(
                new AiTool(
                        SEARCH,
                        "检索当前用户与 Agent 的长期记忆。",
                        schema(Map.of("query", "检索问题", "limit", "返回数量，默认 5"), List.of("query")),
                        null),
                new AiTool(
                        ADD,
                        "写入一条长期记忆事实。",
                        schema(Map.of("content", "记忆内容"), List.of("content")),
                        null));
    }

    private static JsonNode schema(Map<String, String> properties, List<String> required) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        var targets = node.putObject("properties");
        properties.forEach((name, description) -> {
            ObjectNode property = targets.putObject(name);
            property.put("type", "string");
            property.put("description", description);
        });
        var requiredNode = node.putArray("required");
        required.forEach(requiredNode::add);
        return node;
    }
}