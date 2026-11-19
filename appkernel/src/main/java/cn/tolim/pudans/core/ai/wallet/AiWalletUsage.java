package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AiWalletUsage(
        String providerAccount,
        String modelCode,
        String tier,
        @Nullable Integer inputTokens,
        @Nullable Integer cacheTokens,
        @Nullable Integer outputTokens,
        @Nullable String traceId,
        @Nullable String billingSource,
        @Nullable Integer imageCount,
        @Nullable Integer videoSeconds,
        @Nullable Integer audioSeconds,
        @Nullable Integer speechCharacters,
        @Nullable Integer taskCount,
        Map<String, JsonNode> extra) {

    public static final AiWalletUsage EMPTY =
            new AiWalletUsage(null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of());

    public AiWalletUsage {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }

    public AiWalletUsage(
            String providerAccount,
            String modelCode,
            String tier,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable String traceId,
            @Nullable String billingSource) {
        this(providerAccount, modelCode, tier, inputTokens, cacheTokens, outputTokens, traceId, billingSource,
                null, null, null, null, null, Map.of());
    }

    public String remark() {
        var values = new LinkedHashMap<String, Object>();
        put(values, "inputTokens", inputTokens);
        put(values, "cacheTokens", cacheTokens);
        put(values, "outputTokens", outputTokens);
        put(values, "imageCount", imageCount);
        put(values, "videoSeconds", videoSeconds);
        put(values, "audioSeconds", audioSeconds);
        put(values, "speechCharacters", speechCharacters);
        put(values, "taskCount", taskCount);
        if (!extra.isEmpty()) {
            values.put("extra", extra);
        }
        return values.isEmpty() ? null : JsonUtils.objectToJson(values);
    }

    private static void put(Map<String, Object> values, String name, @Nullable Integer value) {
        if (value != null) {
            values.put(name, value);
        }
    }
}
