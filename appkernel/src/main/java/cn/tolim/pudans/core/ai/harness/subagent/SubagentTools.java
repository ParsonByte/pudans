package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class SubagentTools {

    public static final String AGENT_SPAWN = "agent_spawn";

    public static final String AGENT_SEND = "agent_send";

    public static final String AGENT_LIST = "agent_list";

    public static final String AGENT_RELEASE = "agent_release";

    public static final String AGENT_TASK = "agent_task";

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final int MAX_TIMEOUT_SECONDS = 600;

    private static final List<AiTool> DEFINITIONS =
            List.of(spawnTool(), sendTool(), listTool(), releaseTool(), taskTool());

    private final SubagentRuntime runtime;

    public SubagentTools(SubagentRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "子代理运行时不能为空");
    }

    public List<AiTool> definitions() {
        return DEFINITIONS;
    }

    public Mono<HarnessToolkit.ToolResult> execute(
            String toolName, JsonNode arguments, RuntimeContext context) {
        var args = arguments == null ? JsonUtils.mapper().createObjectNode() : arguments;
        var execution = switch (toolName) {
            case AGENT_SPAWN -> spawn(args, context);
            case AGENT_SEND -> send(args, context);
            case AGENT_LIST -> list(context);
            case AGENT_RELEASE -> release(args, context);
            case AGENT_TASK -> task(args, context);
            default -> Mono.just(new HarnessToolkit.ToolResult("未知工具：" + toolName, true));
        };
        return execution.onErrorResume(error -> Mono.just(
                new HarnessToolkit.ToolResult(errorMessage(error), true)));
    }

    private Mono<HarnessToolkit.ToolResult> spawn(JsonNode args, RuntimeContext context) {
        var agentId = text(args, "agent_id");
        if (agentId.isBlank()) {
            return Mono.just(new HarnessToolkit.ToolResult("agent_id 不能为空。", true));
        }
        var timeoutSeconds = timeoutSeconds(args);
        var request = SubagentSpawnRequest.builder()
                .agentId(agentId)
                .task(text(args, "task"))
                .reusable(bool(args, "reusable"))
                .label(nullableText(args, "label"))
                .mode(timeoutSeconds == 0 ? SubagentMode.BACKGROUND : SubagentMode.SYNC)
                .timeout(timeoutSeconds == 0 ? null : Duration.ofSeconds(timeoutSeconds))
                .parentRunId(parentRunId(context))
                .build();
        return runtime.spawn(request, context)
                .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false));
    }

    private Mono<HarnessToolkit.ToolResult> send(JsonNode args, RuntimeContext context) {
        var timeoutSeconds = timeoutSeconds(args);
        var request = SubagentSendRequest.builder()
                .agentKey(nullableText(args, "agent_key"))
                .label(nullableText(args, "label"))
                .message(text(args, "message"))
                .mode(timeoutSeconds == 0 ? SubagentMode.BACKGROUND : SubagentMode.SYNC)
                .timeout(timeoutSeconds == 0 ? null : Duration.ofSeconds(timeoutSeconds))
                .parentRunId(parentRunId(context))
                .build();
        return runtime.send(request, context)
                .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false));
    }

    private Mono<HarnessToolkit.ToolResult> list(RuntimeContext context) {
        return runtime.list(context)
                .collectList()
                .map(handles -> new HarnessToolkit.ToolResult(handlesJson(handles), false));
    }

    private Mono<HarnessToolkit.ToolResult> release(JsonNode args, RuntimeContext context) {
        return runtime.release(nullableText(args, "agent_key"), nullableText(args, "label"), context)
                .thenReturn(new HarnessToolkit.ToolResult("{\"status\":\"released\"}", false));
    }

    private Mono<HarnessToolkit.ToolResult> task(JsonNode args, RuntimeContext context) {
        var taskId = text(args, "task_id");
        if (taskId.isBlank()) {
            return Mono.just(new HarnessToolkit.ToolResult("task_id 不能为空。", true));
        }
        if (!bool(args, "wait")) {
            return runtime.task(taskId, context)
                    .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false));
        }
        var seconds = timeoutSeconds(args);
        var effective = seconds == 0 ? DEFAULT_TIMEOUT_SECONDS : seconds;
        return runtime.awaitTask(taskId, context)
                .timeout(Duration.ofSeconds(effective))
                .map(task -> new HarnessToolkit.ToolResult(taskJson(task), false))
                .onErrorResume(error -> Mono.just(new HarnessToolkit.ToolResult(
                        errorMessage(error), true)));
    }

    private static String taskJson(SubagentTask task) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("agent_key", task.agentKey());
        node.put("agent_id", task.agentId());
        node.put("session_id", task.sessionId());
        if (task.label() != null) {
            node.put("label", task.label());
        }
        if (task.taskId() != null) {
            node.put("task_id", task.taskId());
        }
        node.put("state", task.state().name().toLowerCase(Locale.ROOT));
        node.put("background", task.background());
        if (!task.result().isBlank()) {
            node.put("result", task.result());
        }
        if (!task.error().isBlank()) {
            node.put("error", task.error());
        }
        return node.toString();
    }

    private static String handlesJson(List<SubagentHandle> handles) {
        var array = JsonUtils.mapper().createArrayNode();
        for (var handle : handles) {
            var node = array.addObject();
            node.put("agent_key", handle.agentKey());
            node.put("agent_id", handle.agentId());
            if (handle.label() != null) {
                node.put("label", handle.label());
            }
            node.put("reusable", handle.reusable());
            node.put("state", handle.busy() ? "busy" : "idle");
        }
        return array.toString();
    }

    private static AiTool spawnTool() {
        var schema = schema();
        property(schema, "agent_id", "string", "子代理标识", true);
        property(schema, "task", "string", "交给子代理的初始任务", false);
        property(
                schema,
                "reusable",
                "boolean",
                "任务结束后是否保留并允许 agent_send 继续对话",
                false);
        property(schema, "label", "string", "用于后续 agent_send 或 agent_release 的标签", false);
        property(
                schema,
                "timeout_seconds",
                "integer",
                "同步等待秒数；0 表示后台运行，默认 30，最大 600",
                false);
        return new AiTool(
                AGENT_SPAWN,
                "启动隔离子代理执行任务。子代理不继承父会话历史；"
                        + "reusable=true 时可通过 agent_send 继续对话，"
                        + "timeout_seconds=0 时后台运行并返回 task_id。",
                schema,
                null);
    }

    private static AiTool sendTool() {
        var schema = schema();
        property(schema, "agent_key", "string", "agent_spawn 返回的子代理键，与 label 二选一", false);
        property(schema, "label", "string", "agent_spawn 时设置的标签，与 agent_key 二选一", false);
        property(schema, "message", "string", "发送给子代理的消息", true);
        property(
                schema,
                "timeout_seconds",
                "integer",
                "同步等待秒数；0 表示后台运行，默认 30，最大 600",
                false);
        return new AiTool(
                AGENT_SEND,
                "向已启动的可复用子代理发送后续消息，继续其已有上下文。",
                schema,
                null);
    }

    private static AiTool listTool() {
        return new AiTool(
                AGENT_LIST,
                "列出当前用户和父会话下可寻址的子代理。",
                schema(),
                null);
    }

    private static AiTool releaseTool() {
        var schema = schema();
        property(schema, "agent_key", "string", "子代理键，与 label 二选一", false);
        property(schema, "label", "string", "子代理标签，与 agent_key 二选一", false);
        return new AiTool(
                AGENT_RELEASE,
                "释放空闲子代理并使其句柄失效；运行中的子代理不能释放。",
                schema,
                null);
    }

    private static AiTool taskTool() {
        var schema = schema();
        property(schema, "task_id", "string", "后台子代理任务标识", true);
        property(schema, "wait", "boolean", "是否等待任务完成，默认 false", false);
        property(
                schema,
                "timeout_seconds",
                "integer",
                "等待秒数，默认 30，最大 600",
                false);
        return new AiTool(
                AGENT_TASK,
                "查询后台子代理任务状态；wait=true 时等待任务完成并返回结果。",
                schema,
                null);
    }

    private static ObjectNode schema() {
        var schema = JsonUtils.mapper().createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        return schema;
    }

    private static void property(
            ObjectNode schema, String name, String type, String description, boolean required) {
        var properties = (ObjectNode) schema.get("properties");
        var property = properties.putObject(name);
        property.put("type", type);
        property.put("description", description);
        if (required) {
            ((ArrayNode) schema.get("required")).add(name);
        }
    }

    private static String text(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String nullableText(JsonNode node, String name) {
        var value = text(node, name);
        return value.isBlank() ? null : value.trim();
    }

    private static boolean bool(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static int timeoutSeconds(JsonNode node) {
        var value = node == null ? null : node.get("timeout_seconds");
        if (value == null || value.isNull() || !value.canConvertToInt()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.max(0, Math.min(value.asInt(), MAX_TIMEOUT_SECONDS));
    }

    private static String parentRunId(RuntimeContext context) {
        var value = context.metadata().get(SubagentRuntime.PARENT_RUN_ID_KEY);
        return value == null ? "" : String.valueOf(value);
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message == null || message.isBlank() ? "子代理工具执行失败。" : message;
    }
}