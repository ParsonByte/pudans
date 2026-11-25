package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentRun(
        UUID id,
        UUID sessionId,
        UUID inputMessageId,
        UUID outputMessageId,
        String status,
        UUID modelId,
        String answerMode,
        String reasoning,
        List<String> toolIds,
        UUID quotaReservationId,
        boolean providerRequestSent,
        boolean cancelRequested,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {}
