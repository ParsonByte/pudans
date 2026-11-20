package cn.tolim.pudans.core.ai.harness.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ToolGroupManager {

    private final Map<String, ToolGroup> groups = new LinkedHashMap<>();

    public synchronized ToolGroup create(
            String name, String description, boolean active, ToolGroupScope scope) {
        Objects.requireNonNull(name, "工具组名称不能为空");
        if (groups.containsKey(name)) {
            throw new IllegalArgumentException("工具组已存在：" + name);
        }
        var group = new ToolGroup(name, description, active, scope);
        groups.put(name, group);
        return group;
    }

    public synchronized ToolGroup get(String groupName) {
        return groups.get(groupName);
    }

    public synchronized List<ToolGroup> groups() {
        return List.copyOf(groups.values());
    }

    public synchronized Set<String> metaGroupNames() {
        var names = new LinkedHashSet<String>();
        for (var group : groups.values()) {
            if (group.scope() == ToolGroupScope.META) {
                names.add(group.name());
            }
        }
        return names;
    }

    public synchronized void requireGroup(String groupName) {
        group(groupName);
    }

    public synchronized void addTool(String groupName, String toolName) {
        group(groupName).addTool(toolName);
    }

    public synchronized void setActive(String groupName, boolean active) {
        group(groupName).setActive(active);
    }

    public synchronized boolean isActiveTool(String toolName) {
        var grouped = false;
        for (var group : groups.values()) {
            if (!group.containsTool(toolName)) {
                continue;
            }
            grouped = true;
            if (group.active()) {
                return true;
            }
        }
        return !grouped;
    }

    public synchronized void resetActiveGroups(Collection<String> groupNames) {
        Objects.requireNonNull(groupNames, "工具组名称集合不能为空");
        var requested = new LinkedHashSet<String>();
        for (var groupName : groupNames) {
            var group = group(groupName);
            if (group.scope() != ToolGroupScope.META) {
                throw new IllegalArgumentException("工具组不可由 reset_tools 管理：" + groupName);
            }
            requested.add(groupName);
        }
        for (var group : groups.values()) {
            if (group.scope() == ToolGroupScope.META) {
                group.setActive(requested.contains(group.name()));
            }
        }
    }

    public synchronized String notes() {
        var activated = new StringBuilder("当前激活的工具组：");
        var inactive = new StringBuilder("未激活的工具组：");
        var hasActivated = false;
        var hasInactive = false;
        for (var group : groups.values()) {
            if (group.scope() != ToolGroupScope.META) {
                continue;
            }
            var target = group.active() ? activated : inactive;
            target.append("\n- ").append(group.name()).append("：").append(group.description());
            if (group.active()) {
                hasActivated = true;
            } else {
                hasInactive = true;
            }
        }
        if (!hasActivated) {
            activated.append("\n（无）");
        }
        if (!hasInactive) {
            inactive.append("\n（无）");
        }
        return activated.append("\n").append(inactive).toString();
    }

    private ToolGroup group(String groupName) {
        var group = groups.get(groupName);
        if (group == null) {
            throw new IllegalArgumentException("工具组不存在：" + groupName);
        }
        return group;
    }
}