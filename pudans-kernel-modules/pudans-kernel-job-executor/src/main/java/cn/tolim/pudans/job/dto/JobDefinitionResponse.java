package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;

import java.time.Instant;
import java.time.LocalDateTime;

// Job definition as returned by the API
public record JobDefinitionResponse(
        String id,
        String code,
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
        JobStatus status,
        String description,
        String remark,
        boolean scheduled,
        Instant nextFireTime,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime) {

    public static JobDefinitionResponse from(JobDefinitionEntity entity) {
        return from(entity, false, null);
    }

    public static JobDefinitionResponse from(JobDefinitionEntity entity, boolean scheduled,
                                             Instant nextFireTime) {
        return new JobDefinitionResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getHandlerName(), entity.getScheduleType(), entity.getCronExpression(),
                entity.getPeriodMillis(), entity.getInitialDelayMillis(), entity.getParams(),
                entity.getTimeoutSeconds(), entity.getMaxRetries(),
                entity.getRetryIntervalSeconds(), entity.getBlockStrategy(), entity.getStatus(),
                entity.getDescription(), entity.getRemark(), scheduled, nextFireTime,
                entity.getCreateBy(), entity.getCreateTime(), entity.getUpdateBy(),
                entity.getUpdateTime());
    }
}