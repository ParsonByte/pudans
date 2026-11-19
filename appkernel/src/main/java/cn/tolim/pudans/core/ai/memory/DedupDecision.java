package cn.tolim.pudans.core.ai.memory;

import java.util.List;
import java.util.Objects;

public record DedupDecision(
        String recordId,
        DedupAction action,
        List<String> targetIds,
        String mergedContent,
        MemoryType mergedType,
        Integer mergedPriority,
        List<String> mergedTimestamps) {

    public DedupDecision {
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId 不能为空");
        }
        Objects.requireNonNull(action, "action");
        targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
        if (action.replacesExisting() && targetIds.isEmpty()) {
            throw new IllegalArgumentException(action + " 必须指定 targetIds");
        }
        if (!action.replacesExisting() && !targetIds.isEmpty()) {
            throw new IllegalArgumentException(action + " 不能指定 targetIds");
        }
        if (mergedPriority != null && (mergedPriority < -1 || mergedPriority > 100)) {
            throw new IllegalArgumentException("mergedPriority 必须在 -1 到 100 之间");
        }
        mergedTimestamps = mergedTimestamps == null ? List.of() : List.copyOf(mergedTimestamps);
    }

    public static DedupDecision store(String recordId) {
        return new DedupDecision(recordId, DedupAction.STORE, List.of(), null, null, null, List.of());
    }
}
