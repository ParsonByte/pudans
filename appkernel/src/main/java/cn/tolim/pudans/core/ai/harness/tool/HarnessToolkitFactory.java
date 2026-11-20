package cn.tolim.pudans.core.ai.harness.tool;

import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HarnessToolkitFactory {

    private static final String MCP_PREFIX = "mcp.";

    public HarnessToolkit create(
            List<AiTool> tools,
            HarnessToolkit.ToolHandler handler,
            boolean toolGroupsEnabled,
            List<String> asyncToolPatterns) {
        Objects.requireNonNull(handler, "工具处理器不能为空");
        var toolkit = new HarnessToolkit();
        var patterns = asyncToolPatterns == null
                ? List.<String>of()
                : asyncToolPatterns.stream().filter(StringUtils::hasText).toList();
        if (!patterns.isEmpty()) {
            toolkit.asyncRegistry(new InMemoryAsyncToolRegistry());
        }
        for (var tool : tools == null ? List.<AiTool>of() : tools) {
            var group = group(tool.name(), toolGroupsEnabled, toolkit.groupManager());
            if (ToolPatterns.matches(patterns, tool.name())) {
                toolkit.registerAsync(tool, handler, group);
            } else {
                toolkit.register(tool, handler, group);
            }
        }
        return toolkit.fallbackHandler(handler);
    }

    private static String group(
            String toolName, boolean enabled, ToolGroupManager manager) {
        if (!enabled || !toolName.startsWith(MCP_PREFIX)) {
            return null;
        }
        var parts = toolName.split("\\.");
        if (parts.length < 3 || !StringUtils.hasText(parts[1])) {
            return null;
        }
        var groupName = MCP_PREFIX + parts[1];
        if (manager.get(groupName) == null) {
            manager.create(
                    groupName,
                    "MCP 服务：" + parts[1],
                    true,
                    ToolGroupScope.META);
        }
        return groupName;
    }

    }