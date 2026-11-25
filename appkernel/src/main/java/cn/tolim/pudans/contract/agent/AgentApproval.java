package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentApproval(
        UUID id,
        UUID runId,
        UUID toolCallId,
        String status,
        Map<String, Object> request,
        Map<String, Object> decision,
        String decidedBy,
        Instant decidedAt,
        Instant expiresAt) {}
