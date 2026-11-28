package cn.tolim.pudans.contract.job.dto;

import cn.tolim.pudans.contract.job.enums.ExecutionStatus;
import cn.tolim.pudans.contract.job.enums.TriggerType;

import java.time.LocalDateTime;

// Aggregated execution counters for a single job definition
public record JobExecutionStatsResponse(String jobId,
                                        long totalCount,
                                        long queuedCount,
                                        long runningCount,
                                        long successCount,
                                        long failedCount,
                                        long timeoutCount,
                                        long skippedCount,
                                        long canceledCount,
                                        LastExecution lastExecution) {

    // Compact summary of the most recent execution
    public record LastExecution(String id,
                                ExecutionStatus status,
                                TriggerType triggerType,
                                LocalDateTime startTime,
                                LocalDateTime endTime,
                                Long durationMs) {
    }
}