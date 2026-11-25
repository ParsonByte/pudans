package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentToolCall(
        UUID id,
        UUID runId,
        UUID sessionId,
        String ownerId,
        String workspaceId,
        String toolName,
        boolean mcp,
        String status,
        String idempotencyKey,
        Instant startedAt,
        Instant finishedAt) {}
