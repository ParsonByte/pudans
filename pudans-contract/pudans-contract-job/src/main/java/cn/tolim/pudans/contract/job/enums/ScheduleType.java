package cn.tolim.pudans.contract.job.enums;

// How a job definition is scheduled
public enum ScheduleType {
    CRON,
    FIXED_RATE,
    FIXED_DELAY,
    MANUAL
}