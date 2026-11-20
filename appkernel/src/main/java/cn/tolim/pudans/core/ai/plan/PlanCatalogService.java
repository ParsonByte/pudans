package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.core.extension.ai.AiPlan;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlanCatalogService {

    public static final String CODE_INVALID = "ai-plan-invalid";

    private final ReactiveExtensionClient client;

    public Mono<AiPlan> find(String name) {
        return client.get(AiPlan.class, name);
    }

    public Mono<AiPlan> requireEnabled(String name) {
        return find(name)
                .switchIfEmpty(Mono.error(invalid("套餐 [" + name + "] 不存在。")))
                .flatMap(plan -> validate(plan).thenReturn(plan));
    }

    public Mono<Void> validate(AiPlan plan) {
        return Mono.fromRunnable(() -> requireValid(plan));
    }

    public boolean usable(@Nullable AiPlan plan) {
        return plan != null && violation(plan) == null;
    }

    public static void requireValid(AiPlan plan) {
        var violation = violation(plan);
        if (violation != null) {
            throw invalid(violation);
        }
    }

    private static @Nullable String violation(@Nullable AiPlan plan) {
        if (plan == null || plan.getSpec() == null) {
            return "套餐配置不能为空。";
        }
        var spec = plan.getSpec();
        if (!StringUtils.hasText(spec.getDisplayName())) {
            return "套餐显示名称不能为空。";
        }
        if (!spec.isEnabled()) {
            return "套餐未启用。";
        }
        var quota = spec.getQuota();
        if (quota == null || quota.getFiveHour() == null || quota.getWeekly() == null) {
            return "套餐额度配置不能为空。";
        }
        if (quota.getFiveHour().getLimit() <= 0) {
            return "5 小时额度必须大于零。";
        }
        if (quota.getWeekly().getLimit() < quota.getFiveHour().getLimit()) {
            return "周额度不能小于 5 小时额度。";
        }
        if (quota.getWeekly().getResetDayOfWeek() == null) {
            return "周重置星期不能为空。";
        }
        if (quota.getWeekly().getResetHour() < 0 || quota.getWeekly().getResetHour() > 23) {
            return "周重置小时必须在 0 到 23 之间。";
        }
        if (spec.getToleranceUnits() < 0) {
            return "结算容忍量不能为负数。";
        }
        var vouchers = spec.getVouchers();
        if (vouchers == null) {
            return "套餐重置券配置不能为空。";
        }
        if (vouchers.getFiveHour() < 0 || vouchers.getWeekly() < 0) {
            return "重置券张数不能为负数。";
        }
        if (vouchers.getPeriod() == null) {
            return "重置券周期不能为空。";
        }
        return spec.violation();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }
}