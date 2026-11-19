package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import java.math.BigDecimal;

public final class AiPointsCalculator {

    private AiPointsCalculator() {}

    public static BigDecimal charge(
            AiModel model, boolean free, int inputTokens, int cacheTokens, int outputTokens) {
        if (free) {
            return BigDecimal.ZERO;
        }
        var spec = requireSpec(model);
        var points = segment(inputTokens, spec.getInputTokensPerPoint(), "inputTokensPerPoint")
                + segment(cacheTokens, spec.getCacheTokensPerPoint(), "cacheTokensPerPoint")
                + segment(outputTokens, spec.getOutputTokensPerPoint(), "outputTokensPerPoint");
        return BigDecimal.valueOf(points);
    }

    public static BigDecimal estimate(AiModel model, boolean free, int promptTokens, int maxOutputTokens) {
        return charge(model, free, promptTokens, 0, maxOutputTokens);
    }

    public static BigDecimal charge(AiModel model, boolean free, AiModelUsage usage) {
        if (free || usage == null) {
            return BigDecimal.ZERO;
        }
        var spec = requireSpec(model);
        long points = segment(value(usage.inputTokens()), spec.getInputTokensPerPoint(), "inputTokensPerPoint")
                + segment(value(usage.cacheReadTokens()) + value(usage.cacheWriteTokens()),
                        spec.getCacheTokensPerPoint(), "cacheTokensPerPoint")
                + segment(value(usage.outputTokens()), spec.getOutputTokensPerPoint(), "outputTokensPerPoint")
                + (long) value(usage.imageCount()) * Math.max(0, spec.getImagePointsPerItem())
                + (long) value(usage.videoSeconds()) * Math.max(0, spec.getVideoPointsPerSecond())
                + (long) value(usage.audioSeconds()) * Math.max(0, spec.getAudioPointsPerSecond())
                + (long) ((value(usage.speechCharacters()) + 999) / 1000)
                        * Math.max(0, spec.getSpeechPointsPerKiloCharacter())
                + (long) value(usage.taskCount()) * Math.max(0, spec.getTaskPointsPerInvocation());
        return BigDecimal.valueOf(points);
    }

    public static BigDecimal estimate(AiModel model, boolean free, AiModelUsage usage) {
        return charge(model, free, usage);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long segment(int tokens, int tokensPerPoint, String field) {
        if (tokens <= 0) {
            return 0;
        }
        if (tokensPerPoint <= 0) {
            throw new IllegalArgumentException("模型定价字段 " + field + " 必须大于零。");
        }
        return (tokens + tokensPerPoint - 1L) / tokensPerPoint;
    }

    private static AiModel.Spec requireSpec(AiModel model) {
        if (model == null || model.getSpec() == null) {
            throw new IllegalArgumentException("模型配置不能为空。");
        }
        return model.getSpec();
    }
}
