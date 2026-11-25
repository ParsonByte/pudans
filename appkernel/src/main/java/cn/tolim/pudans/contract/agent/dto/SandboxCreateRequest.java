package cn.tolim.pudans.contract.agent.dto;

import java.util.UUID;

public record SandboxCreateRequest(
        String provider,
        UUID agentSessionId,
        UUID runId,
        String template,
        String region,
        Integer ttlSeconds) {}
