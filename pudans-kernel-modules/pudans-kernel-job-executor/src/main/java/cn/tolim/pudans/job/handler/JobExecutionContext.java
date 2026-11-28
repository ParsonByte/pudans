package cn.tolim.pudans.job.handler;

import cn.tolim.pudans.job.enums.TriggerType;

import java.time.LocalDateTime;

// Immutable context handed to a JobHandler for one execution
public record JobExecutionContext(
        String executionId,
        String jobId,
        String jobCode,
        String jobName,
        String handlerName,
        String params,
        TriggerType triggerType,
        int attempt,
        String instanceName,
        LocalDateTime scheduledTime) {
}