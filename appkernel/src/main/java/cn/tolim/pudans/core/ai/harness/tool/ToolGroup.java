package cn.tolim.pudans.core.ai.harness.tool;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ToolGroup {

    private final String name;

    private final String description;

    private final ToolGroupScope scope;

    private final Set<String> tools = new LinkedHashSet<>();

    private volatile boolean active;

    ToolGroup(String name, String description, boolean active, ToolGroupScope scope) {
        this.name = Objects.requireNonNull(name, "工具组名称不能为空");
        this.description = description == null ? "" : description;
        this.active = active;
        this.scope = Objects.requireNonNull(scope, "工具组作用域不能为空");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ToolGroupScope scope() {
        return scope;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public synchronized boolean containsTool(String toolName) {
        return tools.contains(toolName);
    }

    synchronized void addTool(String toolName) {
        tools.add(Objects.requireNonNull(toolName, "工具名称不能为空"));
    }
}