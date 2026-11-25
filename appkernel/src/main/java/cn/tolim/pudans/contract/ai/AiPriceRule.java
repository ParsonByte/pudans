package cn.tolim.pudans.contract.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiPriceRule(
        UUID id,
        String targetType,
        String targetKey,
        UUID modelId,
        String mcpToolKey,
        String pricingMode,
        BigDecimal inputCoefficient,
        BigDecimal cachedInputCoefficient,
        BigDecimal outputCoefficient,
        BigDecimal mcpCallCoefficient,
        String unit,
        BigDecimal unitCredits,
        BigDecimal baseCredits,
        BigDecimal minimumCredits,
        List<AiPeakWindow> peakWindows,
        BigDecimal peakMultiplier,
        BigDecimal offpeakMultiplier,
        String quotaBucket,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt) {}
