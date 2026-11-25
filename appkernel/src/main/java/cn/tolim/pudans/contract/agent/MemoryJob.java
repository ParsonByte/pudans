package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.UUID;

public record MemoryJob(
        UUID id,
        String jobType,
        String scopeType,
        String scopeId,
        Instant watermarkFrom,
        Instant watermarkTo,
        String status,
        int totalItems,
        int processedItems,
        int failedItems,
        String idempotencyKey,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage) {}
