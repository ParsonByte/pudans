package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentMessage(
        UUID id,
        UUID sessionId,
        UUID runId,
        String role,
        Map<String, Object> content,
        List<UUID> assetIds,
        List<Map<String, Object>> toolCalls,
        Map<String, Object> usage,
        Instant createdAt) {}
