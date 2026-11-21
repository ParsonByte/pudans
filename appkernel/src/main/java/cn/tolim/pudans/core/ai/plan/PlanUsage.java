package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlanUsage {

    private static final String STATE_ACTIVE = "ACTIVE";

    private static final String STATE_IDLE = "IDLE";

    private final PlanResolution planResolution;

    private final PlanCounterRepository counterRepository;

    private final PlanVouchers planVouchers;

    private final PlanWindowClock windowClock;

    public Mono<UsageView> usage(String userId) {
        Assert.hasText(userId, "userId 不能为空");
        return planResolution.resolve(userId).flatMap(resolved -> {
            var plan = resolved.plan();
            var now = windowClock.now();
            return counterRepository
                    .findByUser(userId)
                    .collectList()
                    .flatMap(rows -> planVouchers
                            .totals(userId, plan)
                            .map(vouchers -> new UsageView(
                                    new PlanSummary(
                                            resolved.planName(), resolved.displayName(), resolved.matchedBy()),
                                    fiveHourUsage(plan, rowOf(rows, PlanWindowKind.FIVE_HOUR), now),
                                    weeklyUsage(plan, rowOf(rows, PlanWindowKind.WEEKLY), now),
                                    rateLimit(plan),
                                    vouchers)));
        });
    }

    private WindowUsage fiveHourUsage(
            AiPlan plan, @Nullable PlanCounterRow row, Instant now) {
        var limit = plan.getSpec().getQuota().getFiveHour().getLimit();
        if (row == null || !row.endsAt().isAfter(now)) {
            return new WindowUsage(limit, 0, 0, limit, null, null, STATE_IDLE);
        }
        return new WindowUsage(
                limit,
                row.used(),
                row.reserved(),
                remaining(limit, row.used(), row.reserved()),
                row.startsAt(),
                row.endsAt(),
                STATE_ACTIVE);
    }

    private WindowUsage weeklyUsage(
            AiPlan plan, @Nullable PlanCounterRow row, Instant now) {
        var weekly = plan.getSpec().getQuota().getWeekly();
        var limit = weekly.getLimit();
        var existing = row == null
                ? null
                : new PlanWindowClock.ExistingWindow(
                        row.windowKey(),
                        row.startsAt(),
                        row.endsAt(),
                        PlanWindowClock.ANCHOR_VOUCHER.equals(row.anchorSource()));
        var window = windowClock.weekly(now, weekly, existing);
        var matches = row != null && window.windowKey().equals(row.windowKey());
        var used = matches ? row.used() : 0;
        var reserved = matches ? row.reserved() : 0;
        return new WindowUsage(
                limit,
                used,
                reserved,
                remaining(limit, used, reserved),
                window.startsAt(),
                window.endsAt(),
                matches ? STATE_ACTIVE : STATE_IDLE);
    }

    private static RateLimit rateLimit(AiPlan plan) {
        var configured = plan.getSpec().getRateLimit();
        return configured == null
                ? new RateLimit(0, 0)
                : new RateLimit(configured.getMaxConcurrency(), configured.getMaxRpm());
    }

    private static long remaining(long limit, long used, long reserved) {
        return Math.max(0, limit - used - reserved);
    }

    private static @Nullable PlanCounterRow rowOf(List<PlanCounterRow> rows, PlanWindowKind kind) {
        return rows.stream()
                .filter(row -> kind.name().equals(row.kind()))
                .findFirst()
                .orElse(null);
    }

    public record UsageView(
            PlanSummary plan,
            WindowUsage fiveHour,
            WindowUsage weekly,
            RateLimit rateLimit,
            PlanVouchers.VoucherBalance vouchers) {
    }

    public record RateLimit(int maxConcurrency, int maxRpm) {
    }

    public record PlanSummary(String name, String displayName, String matchedBy) {
    }

    public record WindowUsage(
            long limit,
            long used,
            long reserved,
            long remaining,
            @Nullable Instant startsAt,
            @Nullable Instant endsAt,
            String state) {
    }
}