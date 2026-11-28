package cn.tolim.pudans.job.enums;

// What caused an execution record
public enum TriggerType {

    // Fired by the scheduler
    SCHEDULED,

    // Requested through the API
    MANUAL,

    // Re-run of a failed execution
    RETRY
}