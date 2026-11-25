package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.time.Instant;
import java.util.UUID;

public record GenerationTaskAttemptView(
        UUID id,
        int attemptNo,
        UUID channelId,
        UUID modelId,
        String providerTaskId,
        String providerRequestId,
        GenerationTask.AttemptStatus status,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
