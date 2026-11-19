package cn.tolim.pudans.core.ai.memory;

public enum JevChangeType {
    CONFLICT(4, true),
    BOUNDARY_CHANGE(3, true),
    NEW_RULE(2, true),
    REINFORCEMENT(1, false),
    NONE(0, false);

    private final int priority;
    private final boolean immediateGeneration;

    JevChangeType(int priority, boolean immediateGeneration) {
        this.priority = priority;
        this.immediateGeneration = immediateGeneration;
    }

    public int priority() {
        return priority;
    }

    public boolean requiresImmediateGeneration() {
        return immediateGeneration;
    }
}
