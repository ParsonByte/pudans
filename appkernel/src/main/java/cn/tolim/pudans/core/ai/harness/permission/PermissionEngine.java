package cn.tolim.pudans.core.ai.harness.permission;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PermissionEngine {

    private static final List<String> WRITE_TOOL_MARKERS =
            List.of("write", "edit", "delete", "remove", "exec", "shell", "runtime");

    private final PermissionContext context;

    public PermissionEngine(PermissionContext context) {
        this.context = Objects.requireNonNull(context, "权限上下文不能为空");
    }

    public PermissionContext context() {
        return context;
    }

    public PermissionDecision check(ToolExecutionRequest request) {
        var toolName = request.name();
        if (matches(PermissionBehavior.DENY, toolName, request.arguments())) {
            return PermissionDecision.deny("工具调用被权限规则拒绝：" + toolName + "。");
        }
        if (matches(PermissionBehavior.ASK, toolName, request.arguments())) {
            return PermissionDecision.ask(
                    "工具调用需要用户确认：" + toolName + "。",
                    List.of(new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "suggested")));
        }
        if (context.mode() == PermissionMode.EXPLORE && isWriteTool(toolName)) {
            return PermissionDecision.deny("只读模式下禁止执行：" + toolName + "。");
        }
        if (matches(PermissionBehavior.ALLOW, toolName, request.arguments())) {
            return PermissionDecision.allow("工具调用已放行：" + toolName + "。");
        }
        return switch (context.mode()) {
            case BYPASS -> PermissionDecision.allow(
                    "Bypass 模式放行工具调用：" + toolName + "。");
            case DONT_ASK -> PermissionDecision.deny(
                    "无人值守模式下拒绝未授权工具调用：" + toolName + "。");
            case EXPLORE -> PermissionDecision.ask(
                    "只读模式下需要确认工具调用：" + toolName + "。", List.of());
            case DEFAULT, ACCEPT_EDITS -> PermissionDecision.ask(
                    "工具调用需要用户确认：" + toolName + "。",
                    List.of(new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "suggested")));
        };
    }

    private boolean matches(PermissionBehavior behavior, String toolName, String arguments) {
        for (var rule : context.rules()) {
            if (rule.behavior() != behavior || !rule.toolName().equals(toolName)) {
                continue;
            }
            var content = rule.ruleContent();
            if (content == null
                    || content.isBlank()
                    || (arguments != null && arguments.contains(content))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWriteTool(String toolName) {
        var name = toolName.toLowerCase(Locale.ROOT);
        return WRITE_TOOL_MARKERS.stream().anyMatch(name::contains);
    }
}