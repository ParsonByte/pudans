package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiModel(
        UUID id,
        String logicalName,
        String displayName,
        String description,
        Map<String, Object> capabilities,
        Integer contextWindow,
        Integer maxOutputTokens,
        Map<String, Object> parameterSchema,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
