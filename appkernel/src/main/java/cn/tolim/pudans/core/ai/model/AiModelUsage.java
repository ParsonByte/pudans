package cn.tolim.pudans.core.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AiModelUsage(
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens,
        @Nullable Integer cacheReadTokens,
        @Nullable Integer cacheWriteTokens,
        @Nullable Integer imageCount,
        @Nullable Integer videoSeconds,
        @Nullable Integer audioSeconds,
        @Nullable Integer speechCharacters,
        @Nullable Integer taskCount,
        Map<String, JsonNode> extra) {

    public static final AiModelUsage EMPTY =
            new AiModelUsage(null, null, null, null, null, null, null, null, null, Map.of());

    public AiModelUsage {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public boolean hasAny() {
        return inputTokens != null
                || outputTokens != null
                || cacheReadTokens != null
                || cacheWriteTokens != null
                || imageCount != null
                || videoSeconds != null
                || audioSeconds != null
                || speechCharacters != null
                || taskCount != null;
    }
}
