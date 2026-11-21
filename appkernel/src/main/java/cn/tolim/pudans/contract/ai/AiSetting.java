package cn.tolim.pudans.contract.ai;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiSetting.GROUP,
        version = AiSetting.VERSION,
        kind = AiSetting.KIND,
        plural = "aisettings",
        singular = "aisetting")
public class AiSetting extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiSetting";

    private Spec spec = new Spec();

    @Data
    public static class Spec {

        private String defaultRouteGroup;

        private String defaultChatMode;

        private List<String> defaultKnowledgeBaseCodes = new ArrayList<>();

        private Defaults defaults = new Defaults();

        private Gateway gateway = new Gateway();

        private Embedding embedding = new Embedding();

        private Retrieval retrieval = new Retrieval();

        private Prompt prompt = new Prompt();

        private Routing routing = new Routing();

        private Memory memory = new Memory();

        private Billing billing = new Billing();
    }

    @Data
    public static class Defaults {

        private String team = "default";

        private String agent = "default";
    }

    @Data
    public static class Gateway {

        private boolean injectionEnabled = true;

        private boolean memoryBridgeEnabled = true;

        private int streamIdleTimeoutSeconds = 120;

        private boolean rateLimitEnabled = true;

        private int maxRetries = 2;

        private int retryBackoffMs = 200;

        private List<Integer> retryOnStatuses = new ArrayList<>(List.of(429, 500, 502, 503, 504));

        private int requestTimeoutSeconds = 120;

        private List<AiProvider.Protocol> supportedProtocols =
                new ArrayList<>(List.of(AiProvider.Protocol.OPENAI_CHAT, AiProvider.Protocol.ANTHROPIC));
    }

    @Data
    public static class Embedding {

        private int dimension = 1536;

        private String memoryModel;

        private String knowledgeBaseModel;

        private String queryModel;

        private int batchSize = 64;
    }

    @Data
    public static class Retrieval {

        private String strategy = "HYBRID";

        private int rrfK = 60;

        private int l1Limit = 10;

        private int l2Limit = 3;

        private int orgLimit = 5;

        private double scoreThreshold = 0.30;

        private boolean agenticEnabled;
    }

    @Data
    public static class Prompt {

        private int maxInputTokens = 120000;

        private int maxOutputTokens = 4096;

        private int historyWindowTurns = 20;

        private int knowledgeBaseCharBudget = 4000;
    }

    @Data
    public static class Routing {

        private boolean classifierEnabled = true;

        private AiModel.Tier classifierFallbackTier = AiModel.Tier.HIGH;
    }

    @Data
    public static class Memory {

        private boolean enabled = true;

        private boolean extractionEnabled = true;

        private String extractionRouteGroup;

        private int maxMessagesPerExtraction = 10;

        private int maxBackgroundMessages = 5;

        private int maxMemoriesPerSession = 10;

        private boolean dedupEnabled = true;

        private int conflictRecallTopK = 5;

        private int memoryLimit = 10000;

        private int creditLimit = 1000;

        private boolean promptEnabled = true;

        private boolean generationLogEnabled = true;

        private boolean l2Enabled = true;

        private boolean l3Enabled = true;

        private double jevMinConfidence = 0.70;

        private int l3RefreshHours = 24;

        private boolean decayEnabled = true;

        private int decayMinScore = 20;

        private int decayAccessBoost = 2;

        private int decayDailyAmount = 1;

        private int decayStaleDays = 30;

        private int deleteRetentionDays = 180;

        private int requestMinReasonLength = 10;

        private int orgPromotionThreshold = 3;

        private boolean requireDoubleApprovalForL0;

        private List<String> excludeSessionPrefixes =
                new ArrayList<>(List.of("internal-", "benchmark-"));
    }

    public enum UnassignedPolicy {
        DEFAULT_PLAN,
        DENY
    }

    @Data
    public static class Billing {

        private String defaultPlan;

        private UnassignedPolicy unassignedPolicy = UnassignedPolicy.DEFAULT_PLAN;

        private UsageRatios ratios = new UsageRatios();

        private Map<AiModel.Tier, BigDecimal> tierMultipliers = defaultTierMultipliers();

        private Map<String, BigDecimal> modelMultipliers = new LinkedHashMap<>();

        private int reservationTtlSeconds = 600;

        private int reservationOutputCap = 8192;

        private static Map<AiModel.Tier, BigDecimal> defaultTierMultipliers() {
            var multipliers = new LinkedHashMap<AiModel.Tier, BigDecimal>();
            multipliers.put(AiModel.Tier.CHEAP, new BigDecimal("0.5"));
            multipliers.put(AiModel.Tier.MEDIUM, BigDecimal.ONE);
            multipliers.put(AiModel.Tier.HIGH, new BigDecimal("3.0"));
            multipliers.put(AiModel.Tier.ULTRA, new BigDecimal("9.0"));
            return multipliers;
        }
    }

    @Data
    public static class UsageRatios {

        private BigDecimal input = BigDecimal.ONE;

        private BigDecimal output = new BigDecimal("4.0");

        private BigDecimal cacheRead = new BigDecimal("0.25");

        private BigDecimal cacheWrite = new BigDecimal("1.25");

        private BigDecimal imagePerItem = new BigDecimal("500");

        private BigDecimal videoPerSecond = new BigDecimal("2000");

        private BigDecimal audioPerSecond = new BigDecimal("1000");

        private BigDecimal speechPerKiloChar = new BigDecimal("200");

        private BigDecimal toolCall = new BigDecimal("100");
    }
}
