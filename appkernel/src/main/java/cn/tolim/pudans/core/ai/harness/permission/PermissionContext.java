package cn.tolim.pudans.core.ai.harness.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PermissionContext {

    private final PermissionMode mode;

    private final List<PermissionRule> rules;

    public PermissionContext(PermissionMode mode, List<PermissionRule> rules) {
        this.mode = mode == null ? PermissionMode.DEFAULT : mode;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static PermissionContext bypass() {
        return new PermissionContext(PermissionMode.BYPASS, List.of());
    }

    public PermissionMode mode() {
        return mode;
    }

    public List<PermissionRule> rules() {
        return rules;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private PermissionMode mode = PermissionMode.DEFAULT;

        private final List<PermissionRule> rules = new ArrayList<>();

        public Builder mode(PermissionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder allow(String toolName) {
            rules.add(new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "configured"));
            return this;
        }

        public Builder deny(String toolName) {
            rules.add(new PermissionRule(toolName, null, PermissionBehavior.DENY, "configured"));
            return this;
        }

        public Builder ask(String toolName) {
            rules.add(new PermissionRule(toolName, null, PermissionBehavior.ASK, "configured"));
            return this;
        }

        public Builder rule(PermissionRule rule) {
            rules.add(Objects.requireNonNull(rule, "权限规则不能为空"));
            return this;
        }

        public PermissionContext build() {
            return new PermissionContext(mode, List.copyOf(rules));
        }
    }
}