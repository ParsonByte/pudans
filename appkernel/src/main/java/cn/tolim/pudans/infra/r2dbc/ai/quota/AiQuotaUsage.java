package cn.tolim.pudans.infra.r2dbc.ai.quota;

import java.math.BigDecimal;
import java.time.Instant;

public record AiQuotaUsage(
        BigDecimal credits,
        int toolCalls,
        int mcpCalls,
        Instant oldestEventAt) {

    public static AiQuotaUsage empty() {
        return new AiQuotaUsage(BigDecimal.ZERO, 0, 0, null);
    }
}
