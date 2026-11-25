package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.UUID;

public record MemoryJobItem(
        UUID id,
        UUID jobId,
        String sourceId,
        String stage,
        String status,
        int attempt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {}
