package cn.tolim.pudans.core.ai.memory;

public enum MemoryStatus {
    ACTIVE,
    FROZEN,
    PENDING_CORRECTION,
    DELETED,
    SUPERSEDED;

    public boolean isDedupCandidate() {
        return this == ACTIVE || this == SUPERSEDED;
    }
}
