package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.AiQuotaReservation;
import cn.tolim.pudans.contract.ai.dto.AiQuotaSnapshot;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaReservationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaUsage;
import cn.tolim.pudans.infra.redis.AiQuotaWindowStore;
import cn.tolim.pudans.infra.utils.PudansTime;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiQuotas {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Duration FIVE_HOURS = Duration.ofHours(5);

    private static final Duration WEEKLY = Duration.ofDays(7);

    private static final ZoneId DISPLAY_ZONE = PudansTime.BEIJING;

    private static final DateTimeFormatter WEEKLY_LABEL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DISPLAY_ZONE);

    private final AiQuotaAssignments assignments;

    private final AiQuotaReservationRepository reservations;

    private final AiQuotaWindowStore windows;

    private final AiProperties properties;

    private Clock clock = Clock.systemUTC();

    public Mono<AiQuotaSnapshot> snapshot(RequestIdentity identity, Collection<String> roles) {
        return assignments
                .findForIdentity(identity.ownerId(), identity.workspaceId(), roles)
                .flatMap(this::snapshot)
                .switchIfEmpty(Mono.just(unconfigured()));
    }

    public Mono<AiQuotaSnapshot> snapshot(String subjectType, String subjectId) {
        return assignments
                .find(subjectType, subjectId)
                .flatMap(this::snapshot)
                .switchIfEmpty(Mono.just(unconfigured()));
    }

    public Mono<AiQuotaReservation> reserve(
            RequestIdentity identity,
            Collection<String> roles,
            ReservationRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-reservation-body-required",
                    "配额预扣请求不能为空。"));
        }
        return Mono.defer(() -> {
            var idempotencyKey = AiSupport.requiredText(
                    request.idempotencyKey(),
                    "ai-quota-idempotency-required",
                    "配额预扣幂等键不能为空。");
            return reservations
                    .findByIdempotencyKey(idempotencyKey)
                    .switchIfEmpty(Mono.defer(() -> assignments
                            .findForIdentity(identity.ownerId(), identity.workspaceId(), roles)
                            .switchIfEmpty(Mono.error(notConfigured()))
                            .flatMap(resolved ->
                                    reserveNew(identity, resolved, request, idempotencyKey))));
        });
    }

    public Mono<AiQuotaReservation> settle(
            UUID reservationId,
            BigDecimal actualCredits,
            int actualToolCalls,
            int actualMcpCalls) {
        return Mono.defer(() -> {
            var credits = nonNegative(actualCredits);
            var toolCalls = nonNegative(actualToolCalls, "工具调用次数不能为负数。");
            var mcpCalls = nonNegative(actualMcpCalls, "MCP 调用次数不能为负数。");
            return reservations
                    .findById(reservationId)
                    .switchIfEmpty(Mono.error(reservationNotFound()))
                    .flatMap(existing -> {
                        if (AiSupport.RELEASED.equals(existing.status())) {
                            return Mono.error(new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ai-quota-reservation-released",
                                    "已释放的配额预扣不能结算。"));
                        }
                        var settled = AiSupport.SETTLED.equals(existing.status())
                                ? Mono.just(existing)
                                : reservations.settle(
                                        reservationId,
                                        credits,
                                        toolCalls,
                                        mcpCalls,
                                        Instant.now(clock));
                        return settled.flatMap(result -> windows
                                .settle(scope(result), result.id())
                                .thenReturn(result));
                    })
                    .onErrorMap(
                            error -> !(error instanceof ApiException),
                            this::quotaUnavailable);
        });
    }

    public Mono<AiQuotaReservation> release(UUID reservationId) {
        return reservations
                .findById(reservationId)
                .switchIfEmpty(Mono.error(reservationNotFound()))
                .flatMap(existing -> {
                    var released = AiSupport.RESERVED.equals(existing.status())
                            ? reservations.release(reservationId)
                            : Mono.just(existing);
                    return released.flatMap(result -> windows
                            .release(scope(result), result.id())
                            .thenReturn(result));
                })
                .onErrorMap(
                        error -> !(error instanceof ApiException),
                        this::quotaUnavailable);
    }

    private Mono<AiQuotaReservation> reserveNew(
            RequestIdentity identity,
            AiQuotaAssignments.ResolvedQuota resolved,
            ReservationRequest request,
            String idempotencyKey) {
        var plan = resolved.plan();
        if (!AiSupport.ACTIVE.equals(plan.status()) || !validWindows(plan)) {
            return Mono.error(notConfigured());
        }
        var now = Instant.now(clock);
        var weeklyStart = weeklyPeriodStart(resolved.assignment().weeklyAnchorAt(), now);
        var scope = new AiQuotaWindowStore.Scope(
                resolved.assignment().subjectType(),
                resolved.assignment().subjectId(),
                now,
                weeklyStart);
        var creditDemand = nonNegative(request.credits());
        var toolDemand = nonNegative(request.toolCalls(), "工具调用次数不能为负数。");
        var mcpDemand = nonNegative(request.mcpCalls(), "MCP 调用次数不能为负数。");
        var demand = new AiQuotaWindowStore.Amount(creditDemand, toolDemand, mcpDemand);
        var reservation = new AiQuotaReservation(
                UUID.randomUUID(),
                resolved.assignment().subjectType(),
                resolved.assignment().subjectId(),
                plan.id(),
                request.runId(),
                request.taskId(),
                demand.credits(),
                BigDecimal.ZERO,
                demand.toolCalls(),
                0,
                demand.mcpCalls(),
                0,
                now,
                weeklyStart,
                AiSupport.RESERVED,
                idempotencyKey,
                now.plus(properties.getReservationTtl()),
                now,
                null);
        return reservations
                .settledFiveHourUsage(
                        scope.subjectType(), scope.subjectId(), now.minus(FIVE_HOURS))
                .zipWith(reservations.settledWeeklyUsage(
                        scope.subjectType(), scope.subjectId(), weeklyStart))
                .flatMap(settled -> windows
                        .reserve(
                                scope,
                                new AiQuotaWindowStore.Limit(
                                        plan.fiveHourCredits(),
                                        plan.fiveHourToolCalls(),
                                        plan.fiveHourMcpCalls()),
                                new AiQuotaWindowStore.Limit(
                                        plan.weeklyCredits(),
                                        plan.weeklyToolCalls(),
                                        plan.weeklyMcpCalls()),
                                amount(settled.getT1()),
                                amount(settled.getT2()),
                                demand,
                                reservation.id())
                        .flatMap(result -> result == AiQuotaWindowStore.ReservationResult.RESERVED
                                ? Mono.just(reservation)
                                : Mono.error(new ApiException(
                                        HttpStatus.TOO_MANY_REQUESTS,
                                        "ai-quota-insufficient",
                                        "剩余配额不足。"))))
                .flatMap(ignored -> reservations
                        .insert(reservation)
                        .onErrorResume(error -> windows
                                .release(scope, reservation.id())
                                .then(reservations.findByIdempotencyKey(idempotencyKey))
                                .flatMap(existing -> existing == null
                                        ? Mono.error(error)
                                        : Mono.just(existing))))
                .onErrorMap(
                        error -> !(error instanceof ApiException),
                        this::quotaUnavailable);
    }

    private Mono<AiQuotaSnapshot> snapshot(AiQuotaAssignments.ResolvedQuota resolved) {
        var plan = resolved.plan();
        if (!AiSupport.ACTIVE.equals(plan.status()) || !validWindows(plan)) {
            return Mono.just(unconfigured());
        }
        var now = Instant.now(clock);
        var weeklyStart = weeklyPeriodStart(resolved.assignment().weeklyAnchorAt(), now);
        var subjectType = resolved.assignment().subjectType();
        var subjectId = resolved.assignment().subjectId();
        var weeklyEnd = weeklyStart.plus(WEEKLY);
        return reservations
                .fiveHourUsage(subjectType, subjectId, now.minus(FIVE_HOURS))
                .zipWith(reservations.weeklyUsage(subjectType, subjectId, weeklyStart))
                .map(usage -> {
                    var fiveHourReset = usage.getT1().oldestEventAt() == null
                            ? now.plus(FIVE_HOURS)
                            : usage.getT1().oldestEventAt().plus(FIVE_HOURS);
                    var fiveHour = creditsBucket(
                            usage.getT1().credits(), plan.fiveHourCredits(), fiveHourReset, now);
                    var weekly = weeklyBucket(
                            usage.getT2().credits(), plan.weeklyCredits(), weeklyEnd, now);
                    var toolCalls = strictest(
                            callsBucket(
                                    usage.getT1().toolCalls(),
                                    plan.fiveHourToolCalls(),
                                    fiveHourReset,
                                    now),
                            callsBucket(
                                    usage.getT2().toolCalls(),
                                    plan.weeklyToolCalls(),
                                    weeklyEnd,
                                    now));
                    var mcp = strictest(
                            callsBucket(
                                    usage.getT1().mcpCalls(),
                                    plan.fiveHourMcpCalls(),
                                    fiveHourReset,
                                    now),
                            callsBucket(
                                    usage.getT2().mcpCalls(),
                                    plan.weeklyMcpCalls(),
                                    weeklyEnd,
                                    now));
                    return new AiQuotaSnapshot(fiveHour, weekly, toolCalls, mcp);
                });
    }

    private static AiQuotaSnapshot.AiQuotaBucket creditsBucket(
            BigDecimal used, BigDecimal limit, Instant resetsAt, Instant now) {
        return new AiQuotaSnapshot.AiQuotaBucket(
                remainingPercent(used, limit),
                relativeLabel(resetsAt, now),
                resetsAt,
                used,
                limit,
                "credits",
                true);
    }

    private static AiQuotaSnapshot.AiQuotaBucket weeklyBucket(
            BigDecimal used, BigDecimal limit, Instant resetsAt, Instant now) {
        return new AiQuotaSnapshot.AiQuotaBucket(
                remainingPercent(used, limit),
                WEEKLY_LABEL.format(resetsAt),
                resetsAt,
                used,
                limit,
                "credits",
                true);
    }

    private static AiQuotaSnapshot.AiQuotaBucket callsBucket(
            int used, int limit, Instant resetsAt, Instant now) {
        return new AiQuotaSnapshot.AiQuotaBucket(
                remainingPercent(BigDecimal.valueOf(used), BigDecimal.valueOf(limit)),
                relativeLabel(resetsAt, now),
                resetsAt,
                BigDecimal.valueOf(used),
                BigDecimal.valueOf(limit),
                "calls",
                true);
    }

    private static AiQuotaSnapshot.AiQuotaBucket strictest(
            AiQuotaSnapshot.AiQuotaBucket fiveHour,
            AiQuotaSnapshot.AiQuotaBucket weekly) {
        if (fiveHour.remainingPercent() < weekly.remainingPercent()) {
            return fiveHour;
        }
        if (weekly.remainingPercent() < fiveHour.remainingPercent()) {
            return weekly;
        }
        return fiveHour.resetsAt().isAfter(weekly.resetsAt())
                ? weekly
                : fiveHour;
    }

    private static int remainingPercent(BigDecimal used, BigDecimal limit) {
        if (limit == null || limit.signum() <= 0 || used == null) {
            return 0;
        }
        var remaining = limit.subtract(used);
        if (remaining.signum() <= 0) {
            return 0;
        }
        var percent = remaining
                .multiply(HUNDRED)
                .divide(limit, 0, RoundingMode.FLOOR)
                .intValue();
        return Math.max(0, Math.min(100, percent));
    }

    private static String relativeLabel(Instant resetsAt, Instant now) {
        var remaining = Duration.between(now, resetsAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return "即将恢复";
        }
        var hours = remaining.toHours();
        var minutes = remaining.minusHours(hours).toMinutes();
        if (hours > 0) {
            return minutes > 0
                    ? hours + " 小时 " + minutes + " 分后"
                    : hours + " 小时后";
        }
        return Math.max(1, minutes) + " 分钟后";
    }

    static Instant weeklyPeriodStart(Instant anchor, Instant now) {
        if (anchor == null || !now.isAfter(anchor)) {
            return anchor;
        }
        var elapsed = Duration.between(anchor, now);
        var periods = elapsed.dividedBy(WEEKLY);
        return anchor.plus(WEEKLY.multipliedBy(periods));
    }

    private static boolean validWindows(AiQuotaPlan plan) {
        return Integer.valueOf(18_000).equals(plan.fiveHourWindowSeconds())
                && Integer.valueOf(7).equals(plan.weeklyWindowDays())
                && "assignment_start".equals(plan.weeklyAnchorMode());
    }

    private static AiQuotaWindowStore.Amount amount(AiQuotaUsage usage) {
        return new AiQuotaWindowStore.Amount(
                usage.credits(), usage.toolCalls(), usage.mcpCalls());
    }

    private static AiQuotaWindowStore.Scope scope(AiQuotaReservation reservation) {
        return new AiQuotaWindowStore.Scope(
                reservation.subjectType(),
                reservation.subjectId(),
                reservation.fiveHourBucketAt(),
                reservation.weeklyPeriodStart());
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        var resolved = value == null ? BigDecimal.ZERO : value;
        if (resolved.signum() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-demand-invalid",
                    "配额预扣数量不能为负数。");
        }
        return resolved;
    }

    private static int nonNegative(int value, String message) {
        if (value < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-demand-invalid",
                    message);
        }
        return value;
    }

    private static AiQuotaSnapshot unconfigured() {
        return new AiQuotaSnapshot(
                new AiQuotaSnapshot.AiQuotaBucket(
                        0, "", null, BigDecimal.ZERO, BigDecimal.ZERO, "credits", false),
                new AiQuotaSnapshot.AiQuotaBucket(
                        0, "", null, BigDecimal.ZERO, BigDecimal.ZERO, "credits", false),
                new AiQuotaSnapshot.AiQuotaBucket(
                        0, "", null, BigDecimal.ZERO, BigDecimal.ZERO, "calls", false),
                new AiQuotaSnapshot.AiQuotaBucket(
                        0, "", null, BigDecimal.ZERO, BigDecimal.ZERO, "calls", false));
    }

    private static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ai-quota-not-configured",
                "当前主体尚未配置有效配额。");
    }

    private static ApiException reservationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ai-quota-reservation-not-found",
                "配额预扣记录不存在。");
    }

    private ApiException quotaUnavailable(Throwable error) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ai-quota-unavailable",
                "配额热窗口不可用，已拒绝本次操作。",
                error);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record ReservationRequest(
            UUID runId,
            UUID taskId,
            BigDecimal credits,
            int toolCalls,
            int mcpCalls,
            String idempotencyKey) {}
}
