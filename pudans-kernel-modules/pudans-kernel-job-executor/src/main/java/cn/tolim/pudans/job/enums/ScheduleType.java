package cn.tolim.pudans.job.enums;

// How a job definition is scheduled
public enum ScheduleType {

    // Cron expression scheduling
    CRON,

    // Fixed interval between the starts of consecutive executions
    FIXED_RATE,

    // Fixed interval between the end of an execution and the start of the next one
    FIXED_DELAY,

    // No automatic scheduling, only manual and retry triggers
    MANUAL
}