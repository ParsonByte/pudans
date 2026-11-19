package cn.tolim.pudans.core.ai.memory;

import java.util.List;
import java.util.Objects;

public record RecallResult(
        String prependContext,
        String appendSystemContext,
        List<RecalledMemory> memories,
        String personaText,
        boolean partial) {

    public RecallResult {
        memories = memories == null ? List.of() : List.copyOf(memories);
    }

    public record RecalledMemory(String content, double score, MemoryType type) {

        public RecalledMemory {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("score 必须是有限数值");
            }
        }
    }
}
