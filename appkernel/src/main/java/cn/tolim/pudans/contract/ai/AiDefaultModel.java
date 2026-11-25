package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.UUID;

public record AiDefaultModel(
        UUID id,
        String workspaceId,
        String capability,
        UUID modelId,
        Instant createdAt,
        Instant updatedAt) {}
