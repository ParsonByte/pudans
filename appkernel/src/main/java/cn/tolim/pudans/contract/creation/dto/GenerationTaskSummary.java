package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GenerationTaskSummary(
        UUID id,
        String mediaType,
        String operation,
        UUID modelId,
        String prompt,
        GenerationTask.Status status,
        boolean providerRequestSent,
        List<String> resultAssetIds,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {}
