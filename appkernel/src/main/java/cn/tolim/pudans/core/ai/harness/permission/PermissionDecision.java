package cn.tolim.pudans.core.ai.harness.permission;

import java.util.List;
import java.util.Objects;

public record PermissionDecision(
        PermissionBehavior behavior, String message, List<PermissionRule> suggestedRules) {

    public PermissionDecision {
        behavior = Objects.requireNonNull(behavior, "权限行为不能为空");
        message = message == null ? "" : message;
        suggestedRules = suggestedRules == null ? List.of() : List.copyOf(suggestedRules);
    }

    public static PermissionDecision allow(String message) {
        return new PermissionDecision(PermissionBehavior.ALLOW, message, List.of());
    }

    public static PermissionDecision deny(String message) {
        return new PermissionDecision(PermissionBehavior.DENY, message, List.of());
    }

    public static PermissionDecision ask(String message, List<PermissionRule> suggestedRules) {
        return new PermissionDecision(PermissionBehavior.ASK, message, suggestedRules);
    }
}