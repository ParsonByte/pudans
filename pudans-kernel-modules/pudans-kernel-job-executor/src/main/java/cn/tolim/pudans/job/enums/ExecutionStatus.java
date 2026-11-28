package cn.tolim.pudans.job.enums;

// Lifecycle status of a job execution record
public enum ExecutionStatus {

    // Accepted and waiting for a worker thread
    QUEUED,

    // Handler is currently executing
    RUNNING,

    // Handler completed successfully
    SUCCESS,

    // Handler failed, a retry may follow
    FAILED,

    // Execution exceeded its timeout
    TIMEOUT,

    // Execution was dropped because the previous run still holds the job lock
    SKIPPED,

    // Execution was canceled by an operator or shutdown request
    CANCELED;

    // Whether no further state transition is expected
    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}