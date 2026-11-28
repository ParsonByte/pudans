package cn.tolim.pudans.contract.job.dto;

import cn.tolim.pudans.contract.job.enums.BlockStrategy;
import cn.tolim.pudans.contract.job.enums.JobStatus;
import cn.tolim.pudans.contract.job.enums.ScheduleType;

import java.time.Instant;
import java.time.LocalDateTime;

// Job definition as returned by the API
public record JobDefinitionResponse(String id,
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
}