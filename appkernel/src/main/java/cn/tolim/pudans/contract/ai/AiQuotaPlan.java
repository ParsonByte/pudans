package cn.tolim.pudans.contract.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiQuotaPlan(
        UUID id,
        String name,
        String displayName,
        BigDecimal fiveHourCredits,
        BigDecimal weeklyCredits,
        Integer fiveHourToolCalls,
        Integer weeklyToolCalls,
        Integer fiveHourMcpCalls,
        Integer weeklyMcpCalls,
        Integer fiveHourWindowSeconds,
        Integer weeklyWindowDays,
        String weeklyAnchorMode,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
