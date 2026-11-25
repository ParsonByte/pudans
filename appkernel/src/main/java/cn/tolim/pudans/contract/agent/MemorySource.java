package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MemorySource(
        UUID id,
        String ownerId,
        String workspaceId,
        String sourceKind,
        String sourceId,
        UUID sessionId,
        UUID runId,
        UUID taskId,
        UUID messageStartId,
        UUID messageEndId,
        Instant occurredAt,
        String contentSummary,
        String contentDigest,
        String status,
        Map<String, Object> metadata,
        Instant createdAt) {}
