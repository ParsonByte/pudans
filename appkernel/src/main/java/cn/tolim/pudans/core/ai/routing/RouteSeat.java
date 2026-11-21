package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.ai.AiModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@Builder
public class RouteSeat {

    String provider;

    String account;

    String model;

    AiModel.Tier tier;

    @Builder.Default
    int providerOrder = 0;

    @Nullable
    String fallbackSource;

    @Builder.Default
    int weight = 1;

    boolean free;

    boolean freePlan;

    Long quotaUsed;

    Long quotaLimit;

    Instant resetsAt;

    @Builder.Default
    double pace = 0;

    @Builder.Default
    long ttftMillis = 0;

    @Builder.Default
    double tps = 0;

    @Builder.Default
    long tokensUsed = 0;

    BigDecimal cost;

    @Builder.Default
    boolean resting = false;

    @Builder.Default
    boolean spent = false;

    @Builder.Default
    boolean sunk = false;

    @Builder.Default
    boolean enabled = true;

    @Builder.Default
    double healthScore = 100;

    @Builder.Default
    Set<AiModel.Capability> capabilities = Set.of();

    @Builder.Default
    Set<AiModel.Modality> inputModalities = Set.of();

    @Builder.Default
    Set<AiModel.Modality> outputModalities = Set.of();

    @Builder.Default
    int contextWindow = 0;

    @Builder.Default
    int maxOutputTokens = 0;

    @Builder.Default
    int extraContextMargin = 0;

    public boolean hasQuotaData() {
        return quotaLimit != null && quotaLimit > 0;
    }

    public long remainingQuota() {
        if (quotaLimit == null) {
            return 0;
        }
        long used = quotaUsed == null ? 0 : quotaUsed;
        return Math.max(0, quotaLimit - used);
    }

    public String key() {
        return provider + "/" + account + "/" + model;
    }

    public boolean matches(String reference) {
        return reference != null
                && (reference.equals(model) || reference.equals(provider + "/" + model) || reference.equals(key()));
    }
}
