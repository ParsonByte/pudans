package cn.tolim.pudans.contract.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AiQuotaSnapshot(
        AiQuotaBucket fiveHour,
        AiQuotaBucket weekly,
        AiQuotaBucket toolCalls,
        AiQuotaBucket mcp) {

    public record AiQuotaBucket(
            int remainingPercent,
            String resetLabel,
            Instant resetsAt,
            BigDecimal used,
            BigDecimal limit,
            String unit,
            boolean configured) {}
}
