package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.TriggerType;

import java.time.LocalDateTime;

// One job execution as returned by the API
public record JobExecutionResponse(
        String id,
        String jobId,
        String jobCode,
        String jobName,
        String handlerName,
        TriggerType triggerType,
        ExecutionStatus status,
        int attempt,
        int maxAttempts,
        LocalDateTime scheduledTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String instanceName,
        String params,
        String result,
        String errorMessage,
        String retryOfExecutionId,
        String triggeredBy,
        boolean cancelRequested,
        LocalDateTime createTime) {

    public static JobExecutionResponse from(JobExecutionEntity entity) {
        return from(entity, false);
    }

    public static JobExecutionResponse from(JobExecutionEntity entity, boolean cancelRequested) {
        return new JobExecutionResponse(entity.getId(), entity.getJobId(), entity.getJobCode(),
                entity.getJobName(), entity.getHandlerName(), entity.getTriggerType(),
                entity.getStatus(), entity.getAttempt(), entity.getMaxAttempts(),
                entity.getScheduledTime(), entity.getStartTime(), entity.getEndTime(),
                entity.getDurationMs(), entity.getInstanceName(), entity.getParams(),
                entity.getResult(), entity.getErrorMessage(), entity.getRetryOfExecutionId(),
                entity.getTriggeredBy(), cancelRequested, entity.getCreateTime());
    }
}