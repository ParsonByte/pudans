package cn.tolim.pudans.core.ai.memory;

public enum DedupAction {
    STORE,
    UPDATE,
    MERGE,
    SKIP;

    public boolean replacesExisting() {
        return this == UPDATE || this == MERGE;
    }
}
