package cn.tolim.pudans.core.ai.harness.workspace;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

public final class WorkspaceTools {

    public static final String LIST = "workspace_list";

    public static final String READ = "workspace_read";

    public static final String WRITE = "workspace_write";

    public static final String DELETE = "workspace_delete";

    public static final String MOVE = "workspace_move";

    public static final String SNAPSHOT = "workspace_snapshot";

    private WorkspaceTools() {}

    public static List<AiTool> definitions() {
        return List.of(
                new AiTool(
                        LIST,
                        "列出工作区目录内容。",
                        schema(Map.of("path", "相对路径，默认根目录"), List.of()),
                        null),
                new AiTool(
                        READ,
                        "读取工作区文本文件。",
                        schema(Map.of("path", "相对路径"), List.of("path")),
                        null),
                new AiTool(
                        WRITE,
                        "写入工作区文本文件。",
                        schema(
                                Map.of("path", "相对路径", "content", "文件内容"),
                                List.of("path", "content")),
                        null),
                new AiTool(
                        DELETE,
                        "删除工作区文件或目录。",
                        schema(Map.of("path", "相对路径"), List.of("path")),
                        null),
                new AiTool(
                        MOVE,
                        "移动或重命名工作区文件。",
                        schema(Map.of("from", "源路径", "to", "目标路径"), List.of("from", "to")),
                        null),
                new AiTool(SNAPSHOT, "创建、列出或恢复工作区快照。", snapshotSchema(), null));
    }

    private static JsonNode snapshotSchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        var properties = node.putObject("properties");
        properties.putObject("action")
                .put("type", "string")
                .put("description", "create、list 或 restore，默认 create");
        properties.putObject("id").put("type", "string").put("description", "快照标识，恢复时必填");
        node.putArray("required");
        return node;
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