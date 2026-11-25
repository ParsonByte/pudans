package cn.tolim.pudans.contract.ai.dto;

import cn.tolim.pudans.contract.ai.AiPeakWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiPriceRuleUpdateRequest(
        String targetType,
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
        Instant effectiveTo) {}
