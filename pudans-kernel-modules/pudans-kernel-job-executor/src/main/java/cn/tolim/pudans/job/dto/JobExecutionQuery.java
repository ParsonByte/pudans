package cn.tolim.pudans.job.dto;

import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.TriggerType;

import java.time.LocalDateTime;

// Filter and paging parameters for execution history queries
public record JobExecutionQuery(
        String jobId,
        String jobCode,
        ExecutionStatus status,
        TriggerType triggerType,
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int size) {
}