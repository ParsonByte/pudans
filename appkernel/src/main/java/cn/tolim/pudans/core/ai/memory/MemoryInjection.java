package cn.tolim.pudans.core.ai.memory;

import java.util.List;
import java.util.Objects;

public record MemoryInjection(String prependContext, String appendSystemContext, List<Ref> refs) {

    public MemoryInjection {
        prependContext = prependContext == null ? "" : prependContext;
        appendSystemContext = appendSystemContext == null ? "" : appendSystemContext;
        refs = refs == null ? List.of() : List.copyOf(refs);
    }

    public static MemoryInjection empty() {
        return new MemoryInjection("", "", List.of());
    }

    public boolean isEmpty() {
        return prependContext.isEmpty() && appendSystemContext.isEmpty() && refs.isEmpty();
    }

    public record Ref(Long id, String recordId, String content, MemoryType type, String sceneName, double score) {

        public Ref {
            Objects.requireNonNull(recordId, "recordId");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(type, "type");
            sceneName = sceneName == null ? "" : sceneName;
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("score 必须是有限数值");
            }
        }
    }
}
