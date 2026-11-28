package cn.tolim.pudans.job.scheduling;

import cn.tolim.pudans.job.enums.ScheduleType;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

// Internal handle for one scheduled job managed by the executor
record ScheduledJobHandle(String jobId, String jobCode, ScheduleType scheduleType,
                          ScheduledFuture<?> future, CronExpression cronExpression,
                          AtomicReference<Instant> nextFireTime) {
}