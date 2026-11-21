package cn.tolim.pudans.core.ai.harness.tool;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class MetaToolFactory {

    public static final String RESET_TOOLS = "reset_tools";

    public static final String TOOL_SEARCH = "tool_search";

    public static final String TOOL_TASK = "tool_task";

    private static final String DESCRIPTION_PREFIX =
            "根据当前任务重置可用的工具组。to_activate 是最终保持激活的工具组列表，"
                    + "未列出的 META 工具组会被停用。\n\n";

    private final ToolGroupManager groupManager;

    public MetaToolFactory(ToolGroupManager groupManager) {
        this.groupManager = Objects.requireNonNull(groupManager, "工具组管理器不能为空");
    }

    public AiTool resetToolsTool() {
        var mapper = JsonUtils.mapper();
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var toActivate = schema.putObject("properties").putObject("to_activate");
        toActivate.put("type", "array");
        toActivate.put("description", "最终保持激活的工具组名称列表；传空数组表示停用全部 META 工具组。");
        var items = toActivate.putObject("items");
        items.put("type", "string");
        var metaGroupNames = groupManager.metaGroupNames();
        if (!metaGroupNames.isEmpty()) {
            var values = items.putArray("enum");
            metaGroupNames.forEach(values::add);
        }
        schema.putArray("required").add("to_activate");
        return new AiTool(RESET_TOOLS, DESCRIPTION_PREFIX + groupManager.notes(), schema, null);
    }

    public static AiTool toolSearchTool() {
        var schema = JsonUtils.mapper().createObjectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("query")
                .put("type", "string")
                .put("description", "工具检索关键词");
        properties.putObject("limit")
                .put("type", "integer")
                .put("description", "返回数量，默认 10，最大 50");
        schema.putArray("required").add("query");
        return new AiTool(
                TOOL_SEARCH,
                "按关键词检索当前可用工具，返回工具名称与描述。",
                schema,
                null);
    }

    public static AiTool toolTaskTool() {
        var schema = JsonUtils.mapper().createObjectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("task_id")
                .put("type", "string")
                .put("description", "异步工具任务标识；action=list 时可省略");
        var action = properties.putObject("action");
        action.put("type", "string");
        action.put("description", "read、cancel 或 list，默认 read");
        action.putArray("enum").add("read").add("cancel").add("list");
        properties.putObject("wait")
                .put("type", "boolean")
                .put("description", "read 时是否等待任务完成，默认 false");
        properties.putObject("timeout_seconds")
                .put("type", "integer")
                .put("description", "等待秒数，默认 30，最大 600");
        return new AiTool(
                TOOL_TASK,
                "查询、等待或取消异步工具任务。",
                schema,
                null);
    }

    public HarnessToolkit.ToolResult searchTools(HarnessToolkit toolkit, JsonNode arguments) {
        var query = text(arguments, "query");
        if (query.isBlank()) {
            return new HarnessToolkit.ToolResult("query 不能为空。", true);
        }
        var limit = Math.clamp(intValue(arguments, "limit", 10), 1, 50);
        var result = toolkit.search(query, limit);
        var node = JsonUtils.mapper().createArrayNode();
        for (var name : result.toolNames()) {
            var specification = toolkit.specification(name).orElse(null);
            if (specification == null) {
                continue;
            }
            var item = node.addObject();
            item.put("name", specification.name());
            item.put("description", specification.description() == null
                    ? ""
                    : specification.description());
        }
        return new HarnessToolkit.ToolResult(node.toString(), false);
    }

    public Mono<HarnessToolkit.ToolResult> asyncTask(
            AsyncToolRegistry registry, JsonNode arguments) {
        if (registry == null) {
            return Mono.just(new HarnessToolkit.ToolResult("异步工具注册表未配置。", true));
        }
        var action = text(arguments, "action");
        if (action.isBlank() || "read".equalsIgnoreCase(action)) {
            return readTask(registry, arguments);
        }
        if ("list".equalsIgnoreCase(action)) {
            return registry.list().map(tasks ->
                    new HarnessToolkit.ToolResult(tasksJson(tasks), false));
        }
        if ("cancel".equalsIgnoreCase(action)) {
            var taskId = text(arguments, "task_id");
            if (taskId.isBlank()) {
                return Mono.just(new HarnessToolkit.ToolResult("task_id 不能为空。", true));
            }
            return registry.cancel(taskId).map(cancelled -> new HarnessToolkit.ToolResult(
                    "{\"cancelled\":" + cancelled + "}", false));
        }
        return Mono.just(new HarnessToolkit.ToolResult("未知任务操作：" + action, true));
    }

    private Mono<HarnessToolkit.ToolResult> readTask(
            AsyncToolRegistry registry, JsonNode arguments) {
        var taskId = text(arguments, "task_id");
        if (taskId.isBlank()) {
            return Mono.just(new HarnessToolkit.ToolResult("task_id 不能为空。", true));
        }
        if (!boolValue(arguments, "wait")) {
            return registry.read(taskId)
                    .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false))
                    .switchIfEmpty(Mono.just(
                            new HarnessToolkit.ToolResult("异步工具任务不存在。", true)));
        }
        var seconds = Math.clamp(intValue(arguments, "timeout_seconds", 30), 1, 600);
        return Flux.interval(Duration.ZERO, Duration.ofMillis(200))
                .concatMap(tick -> registry.read(taskId))
                .filter(task -> task.status() != AsyncToolStatus.RUNNING)
                .next()
                .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false))
                .switchIfEmpty(Mono.just(new HarnessToolkit.ToolResult("异步工具任务不存在。", true)))
                .timeout(
                        Duration.ofSeconds(seconds),
                        Mono.just(new HarnessToolkit.ToolResult("等待异步工具任务超时。", true)));
    }

    private static String tasksJson(List<AsyncToolTask> tasks) {
        var node = JsonUtils.mapper().createArrayNode();
        tasks.forEach(task -> node.add(JsonUtils.mapper().valueToTree(taskJsonValue(task))));
        return node.toString();
    }

    private static String taskJson(AsyncToolTask task) {
        return JsonUtils.objectToJson(taskJsonValue(task));
    }

    private static Map<String, Object> taskJsonValue(AsyncToolTask task) {
        return Map.of(
                "task_id", task.id(),
                "tool", task.toolName(),
                "status", task.status().name().toLowerCase(Locale.ROOT),
                "output", task.output(),
                "error", task.error());
    }

    private static String text(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("").strip();
    }

    private static int intValue(JsonNode node, String name, int fallback) {
        var value = node == null ? null : node.get(name);
        return value == null || !value.canConvertToInt() ? fallback : value.asInt();
    }

    private static boolean boolValue(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    public HarnessToolkit.ToolResult resetTools(JsonNode arguments) {
        var toActivate = arguments == null ? null : arguments.get("to_activate");
        if (toActivate == null || !toActivate.isArray()) {
            return new HarnessToolkit.ToolResult("参数 to_activate 必须是工具组名称数组。", true);
        }
        var groupNames = new ArrayList<String>();
        for (var item : toActivate) {
            if (!item.isTextual()) {
                return new HarnessToolkit.ToolResult("工具组名称必须是字符串。", true);
            }
            groupNames.add(item.textValue());
        }
        try {
            groupManager.resetActiveGroups(groupNames);
        } catch (IllegalArgumentException error) {
            return new HarnessToolkit.ToolResult(error.getMessage(), true);
        }
        if (groupNames.isEmpty()) {
            return new HarnessToolkit.ToolResult("所有 META 工具组已停用。", false);
        }
        return new HarnessToolkit.ToolResult(
                "当前激活的工具组：" + String.join("、", groupNames) + "。", false);
    }
}