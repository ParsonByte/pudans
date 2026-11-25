package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentSession(
        UUID id,
        String ownerId,
        String workspaceId,
        String title,
        String profile,
        UUID defaultModelId,
        String status,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt) {}
