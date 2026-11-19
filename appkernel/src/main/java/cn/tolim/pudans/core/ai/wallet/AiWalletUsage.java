package cn.tolim.pudans.core.ai.wallet;

public record AiWalletUsage(
        String providerAccount,
        String modelCode,
        String tier,
        Integer inputTokens,
        Integer cacheTokens,
        Integer outputTokens,
        String traceId,
        String billingSource) {

    public static final AiWalletUsage EMPTY =
            new AiWalletUsage(null, null, null, null, null, null, null, null);
}
