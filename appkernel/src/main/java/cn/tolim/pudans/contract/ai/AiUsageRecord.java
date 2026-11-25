package cn.tolim.pudans.contract.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiUsageRecord(
        UUID id,
        String ownerId,
        String workspaceId,
        UUID runId,
        UUID taskId,
        UUID toolCallId,
        UUID modelId,
        UUID channelId,
        String mcpToolKey,
        BigDecimal units,
        BigDecimal credits,
        Integer toolCalls,
        Integer mcpCalls,
        UUID pricingRuleId,
        String windowKind,
        Instant occurredAt,
        String status,
        String idempotencyKey,
        Instant createdAt) {}
