package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public final class UsageUnitCalculator {

    private final AiSetting.Billing billing;

    private final boolean free;

    public UsageUnitCalculator(AiSetting.Billing billing, boolean free) {
        this.billing = billing == null ? new AiSetting.Billing() : billing;
        this.free = free;
        validateWeights(this.billing.getRatios());
    }

    public BigDecimal estimate(
            int promptTokens, int maxOutputTokens, AiModel.@Nullable Tier tier, @Nullable String modelCode) {
        if (free) {
            return BigDecimal.ZERO;
        }
        var ratios = billing.getRatios();
        var outputTokens = Math.min(Math.max(0, maxOutputTokens), outputCap());
        var weighted = amount(Math.max(0, promptTokens), ratios.getInput())
                .add(amount(outputTokens, ratios.getOutput()));
        return units(weighted, tier, modelCode);
    }

    public BigDecimal charge(
            @Nullable AiModelUsage usage, AiModel.@Nullable Tier tier, @Nullable String modelCode) {
        if (free || usage == null) {
            return BigDecimal.ZERO;
        }
        var ratios = billing.getRatios();
        var weighted = amount(value(usage.inputTokens()), ratios.getInput())
                .add(amount(value(usage.outputTokens()), ratios.getOutput()))
                .add(amount(value(usage.cacheReadTokens()), ratios.getCacheRead()))
                .add(amount(value(usage.cacheWriteTokens()), ratios.getCacheWrite()))
                .add(amount(value(usage.imageCount()), ratios.getImagePerItem()))
                .add(amount(value(usage.videoSeconds()), ratios.getVideoPerSecond()))
                .add(amount(value(usage.audioSeconds()), ratios.getAudioPerSecond()))
                .add(amount(kiloCharacters(value(usage.speechCharacters())), ratios.getSpeechPerKiloChar()))
                .add(amount(value(usage.taskCount()), ratios.getToolCall()));
        return units(weighted, tier, modelCode);
    }

    private BigDecimal units(BigDecimal weighted, AiModel.@Nullable Tier tier, @Nullable String modelCode) {
        var multiplier = tierMultiplier(tier).multiply(modelMultiplier(modelCode));
        return weighted.multiply(multiplier).setScale(0, RoundingMode.CEILING);
    }

    private BigDecimal tierMultiplier(AiModel.@Nullable Tier tier) {
        if (tier == null || billing.getTierMultipliers() == null) {
            return BigDecimal.ONE;
        }
        return positive(billing.getTierMultipliers().get(tier), "档位系数");
    }

    private BigDecimal modelMultiplier(@Nullable String modelCode) {
        if (!StringUtils.hasText(modelCode) || billing.getModelMultipliers() == null) {
            return BigDecimal.ONE;
        }
        return positive(billing.getModelMultipliers().get(modelCode), "模型系数");
    }

    private int outputCap() {
        var cap = billing.getReservationOutputCap();
        return cap > 0 ? cap : Integer.MAX_VALUE;
    }

    private static BigDecimal amount(long quantity, BigDecimal weight) {
        return quantity <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(quantity).multiply(weight);
    }

    private static long kiloCharacters(int characters) {
        return (characters + 999L) / 1000L;
    }

    private static int value(@Nullable Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal positive(@Nullable BigDecimal configured, String name) {
        if (configured == null) {
            return BigDecimal.ONE;
        }
        if (configured.signum() <= 0) {
            throw new IllegalArgumentException(name + "必须大于零。");
        }
        return configured;
    }

    private static void validateWeights(AiSetting.@Nullable UsageRatios ratios) {
        if (ratios == null) {
            throw new IllegalArgumentException("计费比例不能为空。");
        }
        requireWeight(ratios.getInput(), "输入权重");
        requireWeight(ratios.getOutput(), "输出权重");
        requireWeight(ratios.getCacheRead(), "缓存读取权重");
        requireWeight(ratios.getCacheWrite(), "缓存写入权重");
        requireWeight(ratios.getImagePerItem(), "图片权重");
        requireWeight(ratios.getVideoPerSecond(), "视频权重");
        requireWeight(ratios.getAudioPerSecond(), "音频权重");
        requireWeight(ratios.getSpeechPerKiloChar(), "语音权重");
        requireWeight(ratios.getToolCall(), "工具调用权重");
    }

    private static void requireWeight(@Nullable BigDecimal weight, String name) {
        if (weight == null) {
            throw new IllegalArgumentException(name + "不能为空。");
        }
        if (weight.signum() <= 0) {
            throw new IllegalArgumentException(name + "必须大于零。");
        }
    }
}