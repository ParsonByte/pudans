package cn.tolim.pudans.job.enums;

// Lifecycle status of a job definition
public enum JobStatus {

    // Job is eligible for scheduling and manual triggers
    ENABLED,

    // Job is paused and will not be scheduled
    DISABLED
}