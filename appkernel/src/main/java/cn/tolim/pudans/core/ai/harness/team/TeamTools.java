package cn.tolim.pudans.core.ai.harness.team;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class TeamTools {

    public static final String TEAM = "team";

    private static final List<AiTool> DEFINITIONS = List.of(teamTool());

    private final TeamRuntime runtime;

    public TeamTools(TeamRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "团队运行时不能为空");
    }

    public List<AiTool> definitions() {
        return DEFINITIONS;
    }

    public Mono<HarnessToolkit.ToolResult> execute(
            String toolName, JsonNode arguments, RuntimeContext context) {
        if (!TEAM.equals(toolName)) {
            return Mono.just(new HarnessToolkit.ToolResult("未知工具：" + toolName, true));
        }
        var args = arguments == null ? JsonUtils.mapper().createObjectNode() : arguments;
        var action = text(args, "action");
        if (action.isBlank()) {
            return Mono.just(new HarnessToolkit.ToolResult("action 不能为空。", true));
        }
        var userId = context.userId();
        var memberName = text(args, "member_name");
        var execution = switch (normalize(action)) {
            case "listtasks" -> ok(runtime.tasks(userId).map(this::tasksJson));
            case "listclaimabletasks" -> ok(runtime.claimableTasks(userId, memberName).map(this::tasksJson));
            case "createtask" -> ok(runtime.createTask(
                            userId,
                            text(args, "subject"),
                            text(args, "description"),
                            stringArray(args, "blocked_by"),
                            text(args, "owner"))
                    .map(this::taskJson));
            case "assigntask" -> ok(runtime.assignTask(
                            userId,
                            text(args, "task_id"),
                            text(args, "owner"),
                            longValue(args, "expected_version"))
                    .map(this::taskJson));
            case "claimtask" -> ok(runtime.claimTask(
                            userId,
                            memberName,
                            text(args, "task_id"),
                            longValue(args, "expected_version"))
                    .map(this::taskJson));
            case "completetask" -> ok(runtime.completeTask(
                            userId, text(args, "task_id"), text(args, "result"))
                    .map(this::taskJson));
            case "failtask" -> ok(runtime.failTask(
                            userId, text(args, "task_id"), text(args, "result"))
                    .map(this::taskJson));
            case "sendmessage" -> ok(runtime.sendMessage(
                            userId,
                            memberName,
                            text(args, "to_member"),
                            text(args, "content"))
                    .map(this::messageJson));
            case "broadcastmessage" -> ok(runtime.broadcastMessage(
                            userId, memberName, text(args, "content"))
                    .map(this::messagesJson));
            case "inbox" -> ok(runtime.inbox(
                            userId,
                            memberName,
                            intValue(args, "limit", TeamRuntime.DEFAULT_MESSAGE_LIMIT))
                    .map(this::messagesJson));
            case "listmembers" -> ok(runtime.members(userId).map(this::membersJson));
            case "addmember" -> ok(runtime.addMember(
                            userId,
                            memberName,
                            text(args, "agent_ref"),
                            bool(args, "is_lead"))
                    .map(this::memberJson));
            case "removemember" -> runtime.removeMember(userId, memberName)
                    .thenReturn(new HarnessToolkit.ToolResult("{\"ok\":true}", false));
            default -> Mono.just(new HarnessToolkit.ToolResult("未知团队操作：" + action, true));
        };
        return execution.onErrorResume(error -> Mono.just(
                new HarnessToolkit.ToolResult(errorMessage(error), true)));
    }

    private String taskJson(TeamTask task) {
        return taskNode(task).toString();
    }

    private ObjectNode taskNode(TeamTask task) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("task_id", task.taskId());
        node.put("subject", task.subject());
        node.put("description", task.description());
        node.put("state", task.state().name().toLowerCase(Locale.ROOT));
        node.put("owner", task.owner());
        var blockedBy = node.putArray("blocked_by");
        task.blockedBy().forEach(blockedBy::add);
        node.put("result", task.result());
        node.put("version", task.version());
        node.put("created_at", task.createdAt().toString());
        node.put("updated_at", task.updatedAt().toString());
        return node;
    }

    private String tasksJson(List<TeamTask> tasks) {
        var array = JsonUtils.mapper().createArrayNode();
        for (var task : tasks) {
            array.add(taskNode(task));
        }
        return array.toString();
    }

    private String messageJson(TeamMessage message) {
        return messageNode(message).toString();
    }

    private ObjectNode messageNode(TeamMessage message) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("id", message.id());
        node.put("from", message.from());
        node.put("to", message.to());
        node.put("content", message.content());
        node.put("sent_at", message.sentAt().toString());
        return node;
    }

    private String messagesJson(List<TeamMessage> messages) {
        var array = JsonUtils.mapper().createArrayNode();
        for (var message : messages) {
            array.add(messageNode(message));
        }
        return array.toString();
    }

    private String memberJson(TeamMember member) {
        return memberNode(member).toString();
    }

    private ObjectNode memberNode(TeamMember member) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("name", member.name());
        node.put("agent_ref", member.agentRef());
        node.put("lead", member.lead());
        return node;
    }

    private String membersJson(List<TeamMember> members) {
        var array = JsonUtils.mapper().createArrayNode();
        for (var member : members) {
            array.add(memberNode(member));
        }
        return array.toString();
    }

    private static AiTool teamTool() {
        var schema = JsonUtils.mapper().createObjectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        stringProperty(properties, "action", "团队操作名称");
        stringProperty(properties, "task_id", "任务标识");
        stringProperty(properties, "subject", "任务标题");
        stringProperty(properties, "description", "任务说明");
        var blockedBy = properties.putObject("blocked_by");
        blockedBy.put("type", "array");
        blockedBy.put("description", "前置任务标识列表，全部完成后任务才可领取");
        blockedBy.putObject("items").put("type", "string");
        stringProperty(properties, "owner", "指派人或领取人");
        integerProperty(properties, "expected_version", "任务乐观锁版本");
        stringProperty(properties, "result", "完成结果或失败原因");
        stringProperty(properties, "to_member", "邮箱接收成员");
        stringProperty(properties, "content", "邮箱消息内容");
        integerProperty(properties, "limit", "收件箱条数上限");
        stringProperty(properties, "member_name", "当前成员名称");
        stringProperty(properties, "agent_ref", "成员的代理引用");
        booleanProperty(properties, "is_lead", "是否设为主负责人");
        schema.putArray("required").add("action");
        return new AiTool(
                TEAM,
                "团队协作工具。action 可选：listTasks、listClaimableTasks、createTask、assignTask、"
                        + "claimTask、completeTask、failTask、sendMessage、broadcastMessage、inbox、"
                        + "listMembers、addMember、removeMember。",
                schema,
                null);
    }

    private static void stringProperty(ObjectNode properties, String name, String description) {
        var property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    private static void integerProperty(ObjectNode properties, String name, String description) {
        var property = properties.putObject(name);
        property.put("type", "integer");
        property.put("description", description);
    }

    private static void booleanProperty(ObjectNode properties, String name, String description) {
        var property = properties.putObject(name);
        property.put("type", "boolean");
        property.put("description", description);
    }

    private static Mono<HarnessToolkit.ToolResult> ok(Mono<String> output) {
        return output.map(value -> new HarnessToolkit.ToolResult(value, false));
    }

    private static String text(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static boolean bool(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static long longValue(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value != null && !value.isNull() && value.canConvertToLong() ? value.asLong() : 0L;
    }

    private static int intValue(JsonNode node, String name, int defaultValue) {
        var value = node == null ? null : node.get(name);
        return value != null && !value.isNull() && value.canConvertToInt()
                ? value.asInt()
                : defaultValue;
    }

    private static List<String> stringArray(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : value) {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message == null || message.isBlank() ? "团队工具执行失败。" : message;
    }
}