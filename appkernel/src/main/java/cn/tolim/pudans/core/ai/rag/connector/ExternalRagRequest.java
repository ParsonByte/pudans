package cn.tolim.pudans.core.ai.rag.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ExternalRagRequest(
        String query,
        int topK,
        double scoreThreshold,
        Map<String, Object> filters) {

    public ExternalRagRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("外部检索问题不能为空。");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("外部检索数量必须大于 0。");
        }
        if (!Double.isFinite(scoreThreshold) || scoreThreshold < 0 || scoreThreshold > 1) {
            throw new IllegalArgumentException("外部检索分数阈值必须在 0 到 1 之间。");
        }
        filters = filters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(filters));
    }
}