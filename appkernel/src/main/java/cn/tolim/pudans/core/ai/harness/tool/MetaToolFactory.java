package cn.tolim.pudans.core.ai.harness.tool;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Objects;

public final class MetaToolFactory {

    public static final String RESET_TOOLS = "reset_tools";

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