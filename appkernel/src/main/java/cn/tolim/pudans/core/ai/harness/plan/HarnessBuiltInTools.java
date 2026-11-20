package cn.tolim.pudans.core.ai.harness.plan;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class HarnessBuiltInTools {

    public static final String PLAN_ENTER = "plan_enter";

    public static final String PLAN_WRITE = "plan_write";

    public static final String PLAN_EXIT = "plan_exit";

    public static final String TODO_WRITE = "todo_write";

    public static final String READ_TOOL_RESULT = "read_tool_result";

    private HarnessBuiltInTools() {}

    public static ToolSpecification readToolResultSpecification() {
        return ToolSpecification.builder()
                .name(READ_TOOL_RESULT)
                .description("读取已卸载的大工具结果。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("id", "工具结果标识")
                        .required("id")
                        .build())
                .build();
    }

    public static List<ToolSpecification> specifications(boolean planModeEnabled, boolean todoEnabled) {
        var tools = new ArrayList<ToolSpecification>();
        if (planModeEnabled) {
            tools.add(ToolSpecification.builder()
                    .name(PLAN_ENTER)
                    .description("进入只读计划模式。")
                    .build());
            tools.add(ToolSpecification.builder()
                    .name(PLAN_WRITE)
                    .description("写入当前计划内容。")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("content", "计划内容")
                            .required("content")
                            .build())
                    .build());
            tools.add(ToolSpecification.builder()
                    .name(PLAN_EXIT)
                    .description("退出计划模式并进入执行。")
                    .build());
        }
        if (todoEnabled) {
            tools.add(ToolSpecification.builder()
                    .name(TODO_WRITE)
                    .description("替换当前任务清单。")
                    .parameters(JsonObjectSchema.builder()
                            .addProperty("items", dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                                    .items(JsonObjectSchema.builder()
                                            .addStringProperty("id", "任务标识")
                                            .addStringProperty("content", "任务内容")
                                            .addStringProperty("status", "任务状态")
                                            .build())
                                    .build())
                            .required("items")
                            .build())
                    .build());
        }
        return List.copyOf(tools);
    }

    public static Result execute(String toolName, JsonNode arguments, AgentState state) {
        return switch (toolName) {
            case PLAN_ENTER -> enter(state);
            case PLAN_WRITE -> write(arguments, state);
            case PLAN_EXIT -> exit(state);
            case TODO_WRITE -> todo(arguments, state);
            default -> null;
        };
    }

    public static boolean isBuiltIn(String toolName) {
        return PLAN_ENTER.equals(toolName)
                || PLAN_WRITE.equals(toolName)
                || PLAN_EXIT.equals(toolName)
                || TODO_WRITE.equals(toolName);
    }

    public static boolean isReadToolResult(String toolName) {
        return READ_TOOL_RESULT.equals(toolName);
    }

    public static boolean allowedInPlanMode(String toolName) {
        return isBuiltIn(toolName)
                || toolName.contains("read")
                || toolName.contains("get")
                || toolName.contains("list")
                || toolName.contains("search")
                || toolName.contains("find");
    }

    private static Result enter(AgentState state) {
        state.planMode().enter();
        return new Result("已进入计划模式，只允许只读工具和计划/任务工具。", false);
    }

    private static Result write(JsonNode arguments, AgentState state) {
        var content = arguments == null ? null : arguments.get("content");
        if (content == null || !content.isTextual() || !StringUtils.hasText(content.textValue())) {
            return new Result("计划内容不能为空。", true);
        }
        state.planMode().write(content.textValue());
        return new Result("计划已保存。", false);
    }

    private static Result exit(AgentState state) {
        state.planMode().exit();
        return new Result("已退出计划模式。", false);
    }

    private static Result todo(JsonNode arguments, AgentState state) {
        var items = arguments == null ? null : arguments.get("items");
        if (items == null || !items.isArray()) {
            return new Result("任务清单必须是数组。", true);
        }
        var todos = new ArrayList<TodoItem>();
        for (var item : items) {
            if (item == null || !item.isObject()) {
                continue;
            }
            todos.add(new TodoItem(
                    text(item, "id"),
                    text(item, "content"),
                    text(item, "status")));
        }
        state.setTodos(todos);
        return new Result("任务清单已更新。", false);
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public record Result(String output, boolean error) {}
}