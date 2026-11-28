package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.enums.ScheduleType;

import java.time.Instant;

// Description of a job currently held by the scheduler
public record ScheduledJobInfo(String jobId, String jobCode, ScheduleType scheduleType,
                               Instant nextFireTime) {
}