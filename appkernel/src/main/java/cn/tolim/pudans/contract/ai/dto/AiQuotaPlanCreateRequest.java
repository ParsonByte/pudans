package cn.tolim.pudans.contract.ai.dto;

import java.math.BigDecimal;

public record AiQuotaPlanCreateRequest(
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
        String status) {}
