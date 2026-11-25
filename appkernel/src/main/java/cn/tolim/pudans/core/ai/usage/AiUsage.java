package cn.tolim.pudans.core.ai.usage;

import java.util.LinkedHashMap;
import java.util.Map;

public record AiUsage(
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long cachedTokens,
        boolean complete) {

    public AiUsage {
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        cachedTokens = Math.max(0, cachedTokens);
        totalTokens = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
    }

    public static AiUsage of(long inputTokens, long outputTokens, int cachedTokens, int totalTokens) {
        return new AiUsage(inputTokens, outputTokens, totalTokens, cachedTokens, true);
    }

    public boolean usable() {
        return inputTokens + outputTokens > 0;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("inputTokens", inputTokens);
        values.put("outputTokens", outputTokens);
        values.put("cachedTokens", cachedTokens);
        values.put("totalTokens", totalTokens);
        values.put("complete", complete);
        return Map.copyOf(values);
    }
}
