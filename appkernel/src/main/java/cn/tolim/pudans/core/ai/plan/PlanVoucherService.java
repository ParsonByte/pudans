package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.core.extension.ai.AiPlan;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository.VoucherCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository.VoucherUseRow;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PlanVoucherService {

    public static final String CODE_VOUCHER_MISSING = "ai-plan-voucher-missing";

    public static final String CODE_RESET_NOT_NEEDED = "ai-plan-reset-not-needed";

    public static final String CODE_RESET_BUSY = "ai-plan-reset-busy";

    public static final String CODE_VOUCHER_CONFLICT = "ai-plan-voucher-conflict";

    public static final String CODE_GRANT_INVALID = "ai-plan-voucher-grant-invalid";

    private final PlanResolutionService resolutionService;

    private final PlanCounterRepository counterRepository;

    private final PlanVoucherRepository voucherRepository;

    private final PlanWindowClock windowClock;

    private final UserService userService;

    private final UserGroupService userGroupService;

    private final TransactionalOperator transactionalOperator;

    @Autowired
    public PlanVoucherService(
            PlanResolutionService resolutionService,
            PlanCounterRepository counterRepository,
            PlanVoucherRepository voucherRepository,
            PlanWindowClock windowClock,
            UserService userService,
            UserGroupService userGroupService,
            ReactiveTransactionManager transactionManager) {
        this(
                resolutionService,
                counterRepository,
                voucherRepository,
                windowClock,
                userService,
                userGroupService,
                TransactionalOperator.create(transactionManager));
    }

    PlanVoucherService(
            PlanResolutionService resolutionService,
            PlanCounterRepository counterRepository,
            PlanVoucherRepository voucherRepository,
            PlanWindowClock windowClock,
            UserService userService,
            UserGroupService userGroupService,
            TransactionalOperator transactionalOperator) {
        this.resolutionService = resolutionService;
        this.counterRepository = counterRepository;
        this.voucherRepository = voucherRepository;
        this.windowClock = windowClock;
        this.userService = userService;
        this.userGroupService = userGroupService;
        this.transactionalOperator = transactionalOperator;
    }

    public Mono<VoucherBalance> balance(String userId) {
        Assert.hasText(userId, "userId 不能为空");
        return resolutionService.resolve(userId).flatMap(resolved -> totals(userId, resolved.plan()));
    }

    public Mono<VoucherBalance> totals(String userId, AiPlan plan) {
        Assert.hasText(userId, "userId 不能为空");
        var period = periodOf(plan);
        var periodKey = periodKey(period, windowClock.now());
        return Mono.zip(
                        balanceOf(
                                userId,
                                PlanVoucherKind.FIVE_HOUR_RESET,
                                periodKey,
                                entitlement(plan, PlanVoucherKind.FIVE_HOUR_RESET)),
                        balanceOf(
                                userId,
                                PlanVoucherKind.WEEKLY_RESET,
                                periodKey,
                                entitlement(plan, PlanVoucherKind.WEEKLY_RESET)))
                .map(values -> new VoucherBalance(values.getT1(), values.getT2()));
    }

    public Mono<VoucherUseResult> use(String requestId, String userId, PlanVoucherKind kind) {
        Assert.hasText(requestId, "requestId 不能为空");
        Assert.hasText(userId, "userId 不能为空");
        Assert.notNull(kind, "重置券类型不能为空");
        return resolutionService
                .resolve(userId)
                .flatMap(resolved -> {
                    PlanCatalogService.requireValid(resolved.plan());
                    return transactionalOperator.transactional(doUse(requestId, userId, kind, resolved.plan()));
                });
    }

    public Mono<GrantResult> grant(
            PlanVoucherKind kind,
            int count,
            @Nullable List<String> userIds,
            @Nullable List<String> groupNames) {
        Assert.notNull(kind, "重置券类型不能为空");
        if (count <= 0) {
            return Mono.error(invalid("发放张数必须大于零。"));
        }
        var directUsers = distinct(userIds);
        var groups = distinct(groupNames);
        if (directUsers.isEmpty() && groups.isEmpty()) {
            return Mono.error(invalid("必须指定发放用户或用户组。"));
        }
        return validateGroups(groups)
                .then(Mono.defer(() -> groupMemberUids(groups).collectList()))
                .flatMap(memberUids -> {
                    var targets = new LinkedHashSet<String>(directUsers);
                    targets.addAll(memberUids);
                    return Flux.fromIterable(targets)
                            .concatMap(userId -> grantOne(userId, kind, count))
                            .collectList()
                            .map(affected -> new GrantResult(kind, count, affected));
                });
    }

    public Flux<VoucherUseRecord> history(String userId, long beforeId, int limit) {
        Assert.hasText(userId, "userId 不能为空");
        return voucherRepository
                .listUses(userId, beforeId, limit)
                .map(row -> new VoucherUseRecord(
                        row.id(),
                        row.requestId(),
                        row.kind(),
                        row.targetWindowKey(),
                        row.windowKeyAfter(),
                        row.usedAt()));
    }

    private Mono<VoucherUseResult> doUse(
            String requestId, String userId, PlanVoucherKind kind, AiPlan plan) {
        var now = windowClock.now();
        var periodKey = periodKey(periodOf(plan), now);
        return voucherRepository
                .syncEntitlement(userId, kind.name(), periodKey, entitlement(plan, kind))
                .then(voucherRepository.lockCounter(userId, kind.name(), periodKey))
                .switchIfEmpty(Mono.error(new IllegalStateException("重置券计数行不存在。")))
                .flatMap(counter -> lockPlanCounters(userId).flatMap(rows -> voucherRepository
                        .findUse(requestId)
                        .map(existing -> replayUse(existing, userId, kind))
                        .switchIfEmpty(Mono.defer(() -> performUse(
                                requestId, userId, kind, plan, counter, rows, periodKey, now)))));
    }

    private Mono<VoucherUseResult> performUse(
            String requestId,
            String userId,
            PlanVoucherKind kind,
            AiPlan plan,
            VoucherCounterRow counter,
            List<PlanCounterRow> rows,
            String periodKey,
            Instant now) {
        var targetKind = kind.target();
        var five = rowOf(rows, PlanWindowKind.FIVE_HOUR);
        var weekly = rowOf(rows, PlanWindowKind.WEEKLY);
        var target = targetKind == PlanWindowKind.FIVE_HOUR ? five : weekly;
        var quota = plan.getSpec().getQuota();
        var targetWindow = targetKind == PlanWindowKind.FIVE_HOUR
                ? windowClock.fiveHour(now, existing(target))
                : windowClock.weekly(now, quota.getWeekly(), existing(target));
        var fiveWindow = targetKind == PlanWindowKind.FIVE_HOUR
                ? targetWindow
                : windowClock.fiveHour(now, existing(five));
        var weeklyWindow = targetKind == PlanWindowKind.WEEKLY
                ? targetWindow
                : windowClock.weekly(now, quota.getWeekly(), existing(weekly));
        if (counter.available() <= 0) {
            return Mono.error(voucherMissing());
        }
        if (usedIn(target, targetWindow) <= 0) {
            return Mono.error(new ApiException(
                    HttpStatus.CONFLICT, CODE_RESET_NOT_NEEDED, "目标窗口没有用量，无需重置。"));
        }
        if (reservedIn(five, fiveWindow) > 0 || reservedIn(weekly, weeklyWindow) > 0) {
            return Mono.error(new ApiException(
                    HttpStatus.CONFLICT, CODE_RESET_BUSY, "存在进行中的请求，请稍后重试。"));
        }
        var after = targetKind == PlanWindowKind.FIVE_HOUR
                ? windowClock.fiveHourReset(now)
                : windowClock.weeklyReset(now);
        var targetLimit = targetKind == PlanWindowKind.FIVE_HOUR
                ? quota.getFiveHour().getLimit()
                : quota.getWeekly().getLimit();
        var anchor = targetKind == PlanWindowKind.FIVE_HOUR
                ? PlanWindowClock.ANCHOR_PLAN
                : PlanWindowClock.ANCHOR_VOUCHER;
        return voucherRepository
                .consume(userId, kind.name(), periodKey)
                .switchIfEmpty(Mono.error(voucherMissing()))
                .then(counterRepository.resetWindow(
                        userId,
                        targetKind.name(),
                        after.windowKey(),
                        after.startsAt(),
                        after.endsAt(),
                        targetLimit,
                        anchor))
                .then(voucherRepository.insertUseIfAbsent(
                        requestId,
                        userId,
                        kind.name(),
                        periodKey,
                        targetWindow.windowKey(),
                        after.windowKey(),
                        now))
                .flatMap(inserted -> Boolean.TRUE.equals(inserted)
                        ? Mono.just(new VoucherUseResult(
                                requestId,
                                kind,
                                false,
                                targetWindow.windowKey(),
                                after.windowKey(),
                                now))
                        : Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                CODE_VOUCHER_CONFLICT,
                                "相同请求已使用其他参数提交。")));
    }

    private Mono<List<PlanCounterRow>> lockPlanCounters(String userId) {
        return counterRepository
                .ensureRows(userId)
                .then(counterRepository.lockCounter(userId, PlanWindowKind.FIVE_HOUR.name()))
                .switchIfEmpty(Mono.error(new IllegalStateException("5 小时计费窗口不存在。")))
                .flatMap(five -> counterRepository
                        .lockCounter(userId, PlanWindowKind.WEEKLY.name())
                        .switchIfEmpty(Mono.error(new IllegalStateException("周计费窗口不存在。")))
                        .map(weekly -> List.of(five, weekly)));
    }

    private VoucherUseResult replayUse(VoucherUseRow existing, String userId, PlanVoucherKind kind) {
        if (!existing.userId().equals(userId) || !existing.kind().equals(kind.name())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, CODE_VOUCHER_CONFLICT, "相同请求已使用其他参数提交。");
        }
        return new VoucherUseResult(
                existing.requestId(),
                kind,
                true,
                existing.targetWindowKey(),
                existing.windowKeyAfter(),
                existing.usedAt());
    }

    private Mono<String> grantOne(String userId, PlanVoucherKind kind, int count) {
        return resolutionService.resolve(userId).flatMap(resolved -> {
            var plan = resolved.plan();
            PlanCatalogService.requireValid(plan);
            var periodKey = periodKey(periodOf(plan), windowClock.now());
            return voucherRepository
                    .addBonus(userId, kind.name(), periodKey, entitlement(plan, kind), count)
                    .thenReturn(userId);
        });
    }

    private Mono<Void> validateGroups(List<String> groups) {
        return Flux.fromIterable(groups)
                .concatMap(group -> userGroupService
                        .getUserGroup(group)
                        .switchIfEmpty(Mono.error(invalid("用户组 [" + group + "] 不存在。")))
                        .then())
                .then();
    }

    private Flux<String> groupMemberUids(List<String> groups) {
        if (groups.isEmpty()) {
            return Flux.empty();
        }
        return userGroupService
                .listUsernamesOfGroups(groups)
                .concatMap(username -> userService.getUser(username))
                .map(user -> user.getSpec().getUid())
                .filter(StringUtils::hasText)
                .distinct();
    }

    private Mono<Balance> balanceOf(
            String userId, PlanVoucherKind kind, String periodKey, int entitlement) {
        return voucherRepository
                .findCounter(userId, kind.name(), periodKey)
                .map(row -> new Balance(
                        row.entitlement() + row.bonus(), row.used(), Math.max(0, row.available())))
                .switchIfEmpty(Mono.just(new Balance(entitlement, 0, entitlement)));
    }

    private int entitlement(AiPlan plan, PlanVoucherKind kind) {
        var vouchers = plan.getSpec().getVouchers();
        return kind == PlanVoucherKind.FIVE_HOUR_RESET ? vouchers.getFiveHour() : vouchers.getWeekly();
    }

    private static AiPlan.VoucherPeriod periodOf(AiPlan plan) {
        var vouchers = plan.getSpec().getVouchers();
        return vouchers == null || vouchers.getPeriod() == null
                ? AiPlan.VoucherPeriod.MONTHLY
                : vouchers.getPeriod();
    }

    static String periodKey(AiPlan.VoucherPeriod period, Instant now) {
        var date = LocalDate.ofInstant(now, PudansTime.BEIJING);
        if (period == AiPlan.VoucherPeriod.WEEKLY) {
            var weekFields = WeekFields.ISO;
            return String.format(
                    Locale.ROOT,
                    "%d-W%02d",
                    date.get(weekFields.weekBasedYear()),
                    date.get(weekFields.weekOfWeekBasedYear()));
        }
        return String.format(Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue());
    }

    private static PlanCounterRow rowOf(List<PlanCounterRow> rows, PlanWindowKind kind) {
        return rows.stream()
                .filter(row -> kind.name().equals(row.kind()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(kind.name() + " 计费窗口不存在。"));
    }

    private static PlanWindowClock.ExistingWindow existing(PlanCounterRow row) {
        return new PlanWindowClock.ExistingWindow(
                row.windowKey(),
                row.startsAt(),
                row.endsAt(),
                PlanWindowClock.ANCHOR_VOUCHER.equals(row.anchorSource()));
    }

    private static long usedIn(PlanCounterRow row, PlanWindow window) {
        return window.windowKey().equals(row.windowKey()) ? row.used() : 0;
    }

    private static long reservedIn(PlanCounterRow row, PlanWindow window) {
        return window.windowKey().equals(row.windowKey()) ? row.reserved() : 0;
    }

    private static List<String> distinct(@Nullable List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static ApiException voucherMissing() {
        return new ApiException(HttpStatus.CONFLICT, CODE_VOUCHER_MISSING, "没有可用的重置券。");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_GRANT_INVALID, message);
    }

    public record Balance(int total, int used, int remaining) {
    }

    public record VoucherBalance(Balance fiveHour, Balance weekly) {
    }

    public record VoucherUseResult(
            String requestId,
            PlanVoucherKind kind,
            boolean duplicate,
            String targetWindowKey,
            String windowKeyAfter,
            Instant usedAt) {
    }

    public record VoucherUseRecord(
            long id,
            String requestId,
            String kind,
            String targetWindowKey,
            String windowKeyAfter,
            Instant usedAt) {
    }

    public record GrantResult(PlanVoucherKind kind, int count, List<String> userIds) {
    }
}