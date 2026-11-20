package cn.tolim.pudans.core.ai.harness.tool;

import java.util.List;

public record ToolSearchResult(List<String> toolNames) {

    public ToolSearchResult {
        toolNames = List.copyOf(toolNames);
    }
}