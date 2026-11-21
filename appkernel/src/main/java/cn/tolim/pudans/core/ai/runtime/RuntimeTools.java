package cn.tolim.pudans.core.ai.runtime;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

public final class RuntimeTools {

    public static final String EXEC = "runtime_exec";

    public static final String READ = "runtime_read";

    public static final String WRITE = "runtime_write";

    public static final String LOGS = "runtime_logs";

    public static final String STATUS = "runtime_status";

    public static final String START = "runtime_start";

    public static final String STOP = "runtime_stop";

    public static final String SNAPSHOT = "runtime_snapshot";

    public static final String RESTORE = "runtime_restore";

    public static final String SNAPSHOTS = "runtime_snapshots";

    private RuntimeTools() {}

    public static List<AiTool> definitions() {
        return List.of(
                new AiTool(EXEC, "在 Agent 沙箱中执行命令并返回退出码与输出。", execSchema(), null),
                new AiTool(
                        READ,
                        "读取 Agent 沙箱中的文件。",
                        schema(Map.of("path", "沙箱内绝对路径"), List.of("path")),
                        null),
                new AiTool(
                        WRITE,
                        "写入 Agent 沙箱文件。",
                        schema(
                                Map.of(
                                        "path", "沙箱内绝对路径",
                                        "content", "文件内容",
                                        "encoding", "utf-8 或 base64，默认 utf-8"),
                                List.of("path", "content")),
                        null),
                new AiTool(
                        LOGS,
                        "读取 Agent 沙箱最近的运行日志。",
                        schema(Map.of("limit", "返回行数，默认 100"), List.of()),
                        null),
                new AiTool(STATUS, "查询 Agent 沙箱状态。", emptySchema(), null),
                new AiTool(START, "启动 Agent 沙箱。", emptySchema(), null),
                new AiTool(STOP, "停止 Agent 沙箱。", emptySchema(), null),
                new AiTool(
                        SNAPSHOT,
                        "把 Agent 沙箱工作区打包为快照。",
                        schema(Map.of("name", "快照名称，可选"), List.of()),
                        null),
                new AiTool(
                        RESTORE,
                        "把已保存的快照恢复到 Agent 沙箱工作区。",
                        schema(Map.of("snapshotId", "快照标识"), List.of("snapshotId")),
                        null),
                new AiTool(SNAPSHOTS, "列出 Agent 沙箱的历史快照。", emptySchema(), null));
    }

    private static JsonNode execSchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        var properties = node.putObject("properties");
        properties.putObject("command").put("type", "string").put("description", "要执行的命令");
        var args = properties.putObject("args");
        args.put("type", "array");
        args.put("description", "命令参数");
        args.putObject("items").put("type", "string");
        properties.putObject("workdir").put("type", "string").put("description", "工作目录");
        properties.putObject("timeoutSeconds").put("type", "integer").put("description", "超时秒数");
        node.putArray("required").add("command");
        return node;
    }

    private static JsonNode emptySchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        node.putObject("properties");
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