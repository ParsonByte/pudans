package cn.tolim.pudans.contract.core.extension.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
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
        group = AiProviderAccount.GROUP,
        version = AiProviderAccount.VERSION,
        kind = AiProviderAccount.KIND,
        plural = "aiprovideraccounts",
        singular = "aiprovideraccount")
public class AiProviderAccount extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiProviderAccount";

    private Spec spec = new Spec();

    private Status status = new Status();

    public enum AccountType {
        API_KEY,
        OAUTH,
        CUSTOM
    }

    public enum QuotaUnit {
        TOKEN,
        REQUEST,
        CREDIT,
        USD
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String provider;

        private String displayName;

        private AccountType accountType = AccountType.API_KEY;

        private String apiKey;

        private boolean configured;

        private String keyName;

        @Schema(hidden = true)
        @com.fasterxml.jackson.annotation.JsonProperty(
                access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        private AiProvider.Protocol protocolOverride;

        private String keyPlan;

        private int weight = 1;

        private int priority;

        private boolean enabled = true;

        private Integer maxConcurrency = 100;

        private Integer maxRPM = 0;

        private boolean freePlan;

        private boolean creditsEnabled;

        private Quota quota = new Quota();

        private List<QuotaWindow> windows = new ArrayList<>();

        private Balance balance = new Balance();

        private Long spendCapPoints;

        private BalanceProbe balanceProbe = new BalanceProbe();

        private Map<String, Integer> concurrencyByModel = new LinkedHashMap<>();
    }

    @Data
    public static class BalanceProbe {

        private String url;

        private String path;

        private String method = "GET";

        private String token;

        private String headerName = "Authorization";
    }

    @Data
    public static class Quota {

        private QuotaUnit unit = QuotaUnit.TOKEN;

        private Long limit;

        private Long used;

        private Instant resetAt;
    }

    @Data
    public static class QuotaWindow {

        private String name;

        private QuotaUnit unit = QuotaUnit.TOKEN;

        private Long limit;

        private Long used;

        private Instant resetsAt;
    }

    @Data
    public static class Balance {

        private QuotaUnit unit = QuotaUnit.USD;

        private BigDecimal left;
    }

    @Data
    public static class Status {

        private Double healthScore;

        private Instant lastUsedAt;

        private String lastError;
    }
}
