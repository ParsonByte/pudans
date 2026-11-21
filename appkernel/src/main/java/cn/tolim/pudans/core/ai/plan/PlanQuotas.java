package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.UsageLedgerRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.UsageLedgerRepository.LedgerClaim;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

@Service
public class PlanQuotas {

    public static final String CODE_QUOTA_EXCEEDED = "ai-plan-quota-exceeded";

    public static final String CODE_SETTLE_CONFLICT = "ai-plan-settle-conflict";

    public static final String CODE_RESERVE_CONFLICT = "ai-plan-reserve-conflict";

    static final int SWEEP_BATCH_SIZE = 100;

    private static final int DEFAULT_RESERVATION_TTL_SECONDS = 600;

    private static final String BILLING_SCOPE_USER = "USER";

    private static final String STATUS_RESERVED = "RESERVED";

    private static final String STATUS_SETTLED = "SETTLED";

    private final PlanCounterRepository counterRepository;

    private final UsageLedgerRepository ledgerRepository;

    private final AiSettings aiSettings;

    private final PlanWindowClock windowClock;

    private final TransactionalOperator transactionalOperator;

    @Autowired
    public PlanQuotas(
            PlanCounterRepository counterRepository,
            UsageLedgerRepository ledgerRepository,
            AiSettings aiSettings,
            PlanWindowClock windowClock,
            ReactiveTransactionManager transactionManager) {
        this(
                counterRepository,
                ledgerRepository,
                aiSettings,
                windowClock,
                TransactionalOperator.create(transactionManager));
    }

    PlanQuotas(
            PlanCounterRepository counterRepository,
            UsageLedgerRepository ledgerRepository,
            AiSettings aiSettings,
            PlanWindowClock windowClock,
            TransactionalOperator transactionalOperator) {
        this.counterRepository = counterRepository;
        this.ledgerRepository = ledgerRepository;
        this.aiSettings = aiSettings;
        this.windowClock = windowClock;
        this.transactionalOperator = transactionalOperator;
    }

    public Mono<ReserveResult> reserve(
            String requestId,
            String userId,
            AiPlan plan,
            long estimateUnits,
            String requestKind,
            @Nullable String teamId,
            @Nullable String traceId) {
        Assert.hasText(requestId, "requestId 不能为空");
        Assert.hasText(userId, "userId 不能为空");
        Assert.hasText(requestKind, "requestKind 不能为空");
        var units = Math.max(0, estimateUnits);
        var planName = plan.getMetadata() == null ? null : plan.getMetadata().getName();
        return Mono.fromRunnable(() -> PlanCatalog.requireValid(plan))
                .then(Mono.defer(() -> transactionalOperator.transactional(doReserve(
                        requestId, userId, plan, planName, units, requestKind, teamId, traceId))))
                .onErrorResume(
                        DuplicateReservationException.class,
                        error -> replayReserve(requestId, userId));
    }

    public Mono<SettleResult> settle(
            String requestId, long actualUnits, @Nullable PlanUsageSnapshot usage) {
        Assert.hasText(requestId, "requestId 不能为空");
        var units = Math.max(0, actualUnits);
        var snapshot = usage == null ? PlanUsageSnapshot.EMPTY : usage;
        return transactionalOperator
                .transactional(ledgerRepository
                        .claimSettled(requestId, units, snapshot)
                        .flatMap(claim -> settleCounter(
                                        claim.userId(),
                                        PlanWindowKind.FIVE_HOUR,
                                        claim.fiveHourWindowKey(),
                                        claim.reservedUnits(),
                                        units)
                                .then(settleCounter(
                                        claim.userId(),
                                        PlanWindowKind.WEEKLY,
                                        claim.weeklyWindowKey(),
                                        claim.reservedUnits(),
                                        units))
                                .thenReturn(new SettleResult(requestId, units, false))))
                .switchIfEmpty(Mono.defer(() -> replaySettle(requestId)));
    }

    public Mono<ReleaseResult> release(String requestId) {
        Assert.hasText(requestId, "requestId 不能为空");
        return transactionalOperator
                .transactional(ledgerRepository.claimReleased(requestId).flatMap(claim -> releaseCounter(
                                claim.userId(),
                                PlanWindowKind.FIVE_HOUR,
                                claim.fiveHourWindowKey(),
                                claim.reservedUnits())
                        .then(releaseCounter(
                                claim.userId(),
                                PlanWindowKind.WEEKLY,
                                claim.weeklyWindowKey(),
                                claim.reservedUnits()))
                        .thenReturn(new ReleaseResult(requestId, true))))
                .switchIfEmpty(Mono.just(new ReleaseResult(requestId, false)));
    }

    public Mono<SweepResult> sweepExpired() {
        return aiSettings
                .spec()
                .map(spec -> spec.getBilling() == null ? new AiSetting.Billing() : spec.getBilling())
                .defaultIfEmpty(new AiSetting.Billing())
                .flatMap(billing -> {
                    var ttl = billing.getReservationTtlSeconds() > 0
                            ? billing.getReservationTtlSeconds()
                            : DEFAULT_RESERVATION_TTL_SECONDS;
                    var cutoff = windowClock.now().minusSeconds(ttl);
                    return ledgerRepository
                            .findStaleReserved(cutoff, SWEEP_BATCH_SIZE)
                            .concatMap(requestId -> release(requestId).map(ReleaseResult::released))
                            .filter(Boolean::booleanValue)
                            .count()
                            .map(SweepResult::new);
                });
    }

    private Mono<ReserveResult> doReserve(
            String requestId,
            String userId,
            AiPlan plan,
            @Nullable String planName,
            long units,
            String requestKind,
            @Nullable String teamId,
            @Nullable String traceId) {
        return counterRepository
                .ensureRows(userId)
                .then(counterRepository.lockCounter(userId, PlanWindowKind.FIVE_HOUR.name()))
                .switchIfEmpty(Mono.error(new IllegalStateException("5 小时计费窗口不存在。")))
                .flatMap(five -> counterRepository
                        .lockCounter(userId, PlanWindowKind.WEEKLY.name())
                        .switchIfEmpty(Mono.error(new IllegalStateException("周计费窗口不存在。")))
                        .flatMap(weekly -> applyReservation(
                                requestId, userId, plan, planName, units, requestKind, teamId, traceId, five, weekly)));
    }

    private Mono<ReserveResult> applyReservation(
            String requestId,
            String userId,
            AiPlan plan,
            @Nullable String planName,
            long units,
            String requestKind,
            @Nullable String teamId,
            @Nullable String traceId,
            PlanCounterRow five,
            PlanCounterRow weekly) {
        var now = windowClock.now();
        var quota = plan.getSpec().getQuota();
        var fiveReset = !five.endsAt().isAfter(now);
        var weeklyReset = !weekly.endsAt().isAfter(now);
        var fiveWindow = windowClock.fiveHour(now, existing(five));
        var weeklyWindow = windowClock.weekly(now, quota.getWeekly(), existing(weekly));
        requireCapacity(
                PlanWindowKind.FIVE_HOUR,
                quota.getFiveHour().getLimit(),
                fiveReset ? 0 : five.used(),
                fiveReset ? 0 : five.reserved(),
                units,
                fiveWindow.endsAt());
        requireCapacity(
                PlanWindowKind.WEEKLY,
                quota.getWeekly().getLimit(),
                weeklyReset ? 0 : weekly.used(),
                weeklyReset ? 0 : weekly.reserved(),
                units,
                weeklyWindow.endsAt());
        return applyWindow(
                        five,
                        fiveReset,
                        fiveWindow,
                        quota.getFiveHour().getLimit(),
                        PlanWindowClock.ANCHOR_PLAN)
                .then(applyWindow(
                        weekly,
                        weeklyReset,
                        weeklyWindow,
                        quota.getWeekly().getLimit(),
                        weekly.anchorSource()))
                .then(Mono.defer(() -> counterRepository
                        .reserve(userId, PlanWindowKind.FIVE_HOUR.name(), fiveWindow.windowKey(), units)
                        .then(counterRepository.reserve(
                                userId, PlanWindowKind.WEEKLY.name(), weeklyWindow.windowKey(), units))
                        .then(ledgerRepository.insertReserved(
                                requestId,
                                userId,
                                teamId,
                                planName,
                                BILLING_SCOPE_USER,
                                requestKind,
                                fiveWindow.windowKey(),
                                weeklyWindow.windowKey(),
                                units,
                                traceId))
                        .flatMap(inserted -> Boolean.TRUE.equals(inserted)
                                ? Mono.just(new ReserveResult(
                                        requestId,
                                        userId,
                                        planName,
                                        units,
                                        fiveWindow.windowKey(),
                                        weeklyWindow.windowKey(),
                                        false))
                                : Mono.error(new DuplicateReservationException()))));
    }

    private Mono<Void> applyWindow(
            PlanCounterRow row, boolean reset, PlanWindow window, long limit, String anchorSource) {
        if (reset) {
            return counterRepository.resetWindow(
                    row.userId(),
                    row.kind(),
                    window.windowKey(),
                    window.startsAt(),
                    window.endsAt(),
                    limit,
                    anchorSource);
        }
        if (row.quotaLimit() != limit) {
            return counterRepository.syncQuotaLimit(row.userId(), row.kind(), limit);
        }
        return Mono.empty();
    }

    private Mono<Void> settleCounter(
            String userId,
            PlanWindowKind kind,
            @Nullable String windowKey,
            long reservedUnits,
            long actualUnits) {
        if (windowKey == null) {
            return Mono.empty();
        }
        return counterRepository.settle(userId, kind.name(), windowKey, reservedUnits, actualUnits);
    }

    private Mono<Void> releaseCounter(
            String userId,
            PlanWindowKind kind,
            @Nullable String windowKey,
            long reservedUnits) {
        if (windowKey == null) {
            return Mono.empty();
        }
        return counterRepository.release(userId, kind.name(), windowKey, reservedUnits);
    }

    private Mono<ReserveResult> replayReserve(String requestId, String userId) {
        return ledgerRepository
                .findByRequestId(requestId)
                .flatMap(row -> {
                    if (!row.userId().equals(userId)) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, CODE_RESERVE_CONFLICT, "预留请求已绑定其他用户。"));
                    }
                    if (!STATUS_RESERVED.equals(row.status())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, CODE_RESERVE_CONFLICT, "预留请求已结束，不能重复使用。"));
                    }
                    return Mono.just(new ReserveResult(
                            requestId,
                            userId,
                            row.planId(),
                            row.units(),
                            row.fiveHourWindowKey(),
                            row.weeklyWindowKey(),
                            true));
                })
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT, CODE_RESERVE_CONFLICT, "预留请求缺少账本记录。")));
    }

    private Mono<SettleResult> replaySettle(String requestId) {
        return ledgerRepository
                .findByRequestId(requestId)
                .flatMap(row -> {
                    if (STATUS_SETTLED.equals(row.status())) {
                        return Mono.just(new SettleResult(requestId, row.units(), true));
                    }
                    return Mono.error(new ApiException(
                            HttpStatus.CONFLICT, CODE_SETTLE_CONFLICT, "结算请求已释放或状态异常。"));
                })
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT, CODE_SETTLE_CONFLICT, "结算请求不存在。")));
    }

    private static void requireCapacity(
            PlanWindowKind kind,
            long limit,
            long used,
            long reserved,
            long estimate,
            Instant resetsAt) {
        var remaining = Math.max(0, limit - used - reserved);
        if (estimate > remaining) {
            throw new PlanQuotaExceededException(kind, remaining, resetsAt);
        }
    }

    private static PlanWindowClock.ExistingWindow existing(PlanCounterRow row) {
        return new PlanWindowClock.ExistingWindow(
                row.windowKey(),
                row.startsAt(),
                row.endsAt(),
                PlanWindowClock.ANCHOR_VOUCHER.equals(row.anchorSource()));
    }

    public record ReserveResult(
            String requestId,
            String userId,
            @Nullable String planId,
            long units,
            @Nullable String fiveHourWindowKey,
            @Nullable String weeklyWindowKey,
            boolean duplicate) {
    }

    public record SettleResult(String requestId, long units, boolean duplicate) {
    }

    public record ReleaseResult(String requestId, boolean released) {
    }

    public record SweepResult(long releasedCount) {
    }

    private static final class DuplicateReservationException extends RuntimeException {}
}