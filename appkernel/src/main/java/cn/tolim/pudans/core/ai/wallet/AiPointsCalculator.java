package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
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
