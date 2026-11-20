package cn.tolim.pudans.core.ai.harness.permission;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PermissionRule(
        String toolName,
        @Nullable String ruleContent,
        PermissionBehavior behavior,
        String source) {

    public PermissionRule {
        toolName = Objects.requireNonNull(toolName, "工具名称不能为空");
        behavior = Objects.requireNonNull(behavior, "权限行为不能为空");
        source = source == null ? "default" : source;
    }
}