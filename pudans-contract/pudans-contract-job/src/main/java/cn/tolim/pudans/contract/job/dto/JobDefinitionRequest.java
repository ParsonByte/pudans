package cn.tolim.pudans.contract.job.dto;

import cn.tolim.pudans.contract.job.enums.BlockStrategy;
import cn.tolim.pudans.contract.job.enums.ScheduleType;

// Create or replace a job definition
public record JobDefinitionRequest(String code,
                                   String name,
                                   String handlerName,
                                   ScheduleType scheduleType,
                                   String cronExpression,
                                   Long periodMillis,
                                   Long initialDelayMillis,
                                   String params,
                                   Integer timeoutSeconds,
                                   Integer maxRetries,
                                   Integer retryIntervalSeconds,
                                   BlockStrategy blockStrategy,
                                   Boolean enabled,
                                   String description,
                                   String remark) {
}