package cn.tolim.pudans.core.ai.harness.team;

public enum TeamTaskState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED;
    }
}