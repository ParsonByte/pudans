package cn.tolim.pudans.core.agent.tool;

import java.util.UUID;

public record AgentToolContext(
        UUID runId,
        UUID sessionId,
        String ownerId,
        String workspaceId,
        UUID quotaReservationId) {}
