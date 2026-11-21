package cn.tolim.pudans.contract.ai;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = AiPlan.GROUP,
        version = AiPlan.VERSION,
        kind = AiPlan.KIND,
        plural = "aiplans",
        singular = "aiplan")
public class AiPlan extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "AiPlan";
    public static final String CODE_MODEL_DENIED = "ai-plan-model-denied";

    private Spec spec = new Spec();

    public enum VoucherPeriod {
        WEEKLY,
        MONTHLY
    }

    public enum BillingScope {
        USER,
        SYSTEM
    }

    public enum FallbackAction {
        DENY,
        DOWNGRADE
    }

    public enum Feature {
        CHAT,
        KNOWLEDGE,
        AGENT,
        SUBAGENT
    }

    @Data
    public static class Spec {

        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        private boolean enabled = true;

        private Quota quota = new Quota();

        private long toleranceUnits = 5000;

        private Vouchers vouchers = new Vouchers();

        private RateLimit rateLimit = new RateLimit();

        private Fallback fallback = new Fallback();

        private Access access = new Access();

        // 新增字段的套餐校验，由网关与生成入口在解析套餐后执行
        public @Nullable String violation() {
            var rateLimitConfig = rateLimit == null ? new RateLimit() : rateLimit;
            if (rateLimitConfig.getMaxConcurrency() < 0) {
                return "套餐并发上限不能为负数。";
            }
            if (rateLimitConfig.getMaxRpm() < 0) {
                return "套餐每分钟请求上限不能为负数。";
            }
            var fallbackConfig = fallback == null ? new Fallback() : fallback;
            if (fallbackConfig.getOnFiveHourExhausted() == null) {
                return "5 小时额度耗尽策略不能为空。";
            }
            if (fallbackConfig.getOnFiveHourExhausted() == FallbackAction.DOWNGRADE
                    && !StringUtils.hasText(fallbackConfig.getDowngradeRouteGroup())) {
                return "降级策略必须指定降级路由组。";
            }
            var accessConfig = access == null ? new Access() : access;
            if (hasBlank(accessConfig.getRouteGroups())) {
                return "套餐允许的路由组不能包含空值。";
            }
            if (hasBlank(accessConfig.getModels())) {
                return "套餐允许的模型不能包含空值。";
            }
            if (accessConfig.getFeatures() != null
                    && accessConfig.getFeatures().stream().anyMatch(Objects::isNull)) {
                return "套餐允许的功能不能包含空值。";
            }
            return null;
        }

        private static boolean hasBlank(@Nullable List<String> values) {
            return values != null && values.stream().anyMatch(value -> !StringUtils.hasText(value));
        }
    }

    @Data
    public static class Quota {

        private FiveHour fiveHour = new FiveHour();

        private Weekly weekly = new Weekly();
    }

    @Data
    public static class FiveHour {

        @Schema(requiredMode = REQUIRED, minimum = "1")
        private long limit;
    }

    @Data
    public static class Weekly {

        @Schema(requiredMode = REQUIRED, minimum = "1")
        private long limit;

        private DayOfWeek resetDayOfWeek = DayOfWeek.MONDAY;

        @Schema(minimum = "0", maximum = "23")
        private int resetHour;
    }

    @Data
    public static class Vouchers {

        @Schema(minimum = "0")
        private int fiveHour;

        @Schema(minimum = "0")
        private int weekly;

        private VoucherPeriod period = VoucherPeriod.MONTHLY;
    }

    @Data
    public static class RateLimit {

        // 零表示不限制
        @Schema(minimum = "0")
        private int maxConcurrency;

        @Schema(minimum = "0")
        private int maxRpm;
    }

    @Data
    public static class Fallback {

        private FallbackAction onFiveHourExhausted = FallbackAction.DENY;

        private String downgradeRouteGroup;

        public boolean downgrades() {
            return onFiveHourExhausted == FallbackAction.DOWNGRADE
                    && StringUtils.hasText(downgradeRouteGroup);
        }
    }

    @Data
    public static class Access {

        // 空列表表示不限制
        private List<String> routeGroups = new ArrayList<>();

        private List<String> models = new ArrayList<>();

        private List<Feature> features = new ArrayList<>();

        public boolean allowsRouteGroup(@Nullable String group) {
            return routeGroups == null
                    || routeGroups.isEmpty()
                    || (group != null && routeGroups.contains(group));
        }

        public boolean allowsModel(@Nullable String model) {
            return models == null || models.isEmpty() || (model != null && models.contains(model));
        }

        public boolean allowsFeature(@Nullable Feature feature) {
            return features == null || features.isEmpty() || (feature != null && features.contains(feature));
        }
    }
}