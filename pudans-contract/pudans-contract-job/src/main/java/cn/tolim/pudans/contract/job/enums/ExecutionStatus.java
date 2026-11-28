package cn.tolim.pudans.contract.job.enums;

// Lifecycle status of a job execution record
public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    SKIPPED,
    CANCELED
}