package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import org.jspecify.annotations.Nullable;

public record PlanUsageSnapshot(
        @Nullable String provider,
        @Nullable String providerAccount,
        @Nullable String modelCode,
        AiModel.@Nullable Tier tier,
        @Nullable Integer inputTokens,
        @Nullable Integer cacheTokens,
        @Nullable Integer outputTokens,
        @Nullable String traceId) {

    public static final PlanUsageSnapshot EMPTY =
            new PlanUsageSnapshot(null, null, null, null, null, null, null, null);
}