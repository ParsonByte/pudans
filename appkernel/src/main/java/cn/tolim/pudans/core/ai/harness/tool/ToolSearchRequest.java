package cn.tolim.pudans.core.ai.harness.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Objects;

public record ToolSearchRequest(List<ToolSpecification> candidates, String query, int limit) {

    public ToolSearchRequest {
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(query, "检索词不能为空");
        if (query.isBlank()) {
            throw new IllegalArgumentException("检索词不能为空");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("结果数量必须大于 0");
        }
    }
}