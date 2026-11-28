package cn.tolim.pudans.job.dto;

import java.time.Instant;
import java.util.List;

// Overall executor status including scheduler, pool and job counters
public record ExecutorStatusResponse(
        String instanceName,
        boolean enabled,
        int scheduledJobCount,
        long jobDefinitionCount,
        long enabledJobCount,
        long queuedExecutionCount,
        long runningExecutionCount,
        int workerPoolSize,
        int workerActiveThreads,
        int workerQueueSize,
        String zone,
        List<String> handlerNames,
        Instant generatedAt) {
}