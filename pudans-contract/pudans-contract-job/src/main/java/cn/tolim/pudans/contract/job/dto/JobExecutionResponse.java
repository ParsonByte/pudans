package cn.tolim.pudans.contract.job.dto;

import cn.tolim.pudans.contract.job.enums.ExecutionStatus;
import cn.tolim.pudans.contract.job.enums.TriggerType;

import java.time.LocalDateTime;

// One job execution as returned by the API
public record JobExecutionResponse(String id,
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
}