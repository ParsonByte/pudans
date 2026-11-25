package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanUpdateRequest;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaAssignmentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaPlanRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiQuotaPlans {

    private static final int FIVE_HOUR_SECONDS = 18_000;

    private static final int WEEKLY_DAYS = 7;

    private static final String WEEKLY_ANCHOR_MODE = "assignment_start";

    private final AiQuotaPlanRepository plans;

    private final AiQuotaAssignmentRepository assignments;

    private final AiQuotaReservationRepository reservations;

    private Clock clock = Clock.systemUTC();

    public Flux<AiQuotaPlan> list() {
        return plans.findAll();
    }

    public Mono<AiQuotaPlan> get(UUID id) {
        return plans
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-quota-plan-not-found", "配额套餐不存在。")));
    }

    public Mono<AiQuotaPlan> create(AiQuotaPlanCreateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            var plan = new AiQuotaPlan(
                    UUID.randomUUID(),
                    AiSupport.requiredText(
                            request.name(),
                            "ai-quota-plan-name-required",
                            "配额套餐名称不能为空。"),
                    AiSupport.requiredText(
                            request.displayName(),
                            "ai-quota-plan-display-name-required",
                            "配额套餐显示名称不能为空。"),
                    nonNegative(
                            request.fiveHourCredits(),
                            "ai-quota-plan-credits-invalid",
                            "5 小时积分不能为负数。"),
                    nonNegative(
                            request.weeklyCredits(),
                            "ai-quota-plan-credits-invalid",
                            "周积分不能为负数。"),
                    nonNegative(
                            request.fiveHourToolCalls(),
                            "ai-quota-plan-tool-calls-invalid",
                            "5 小时工具调用次数不能为负数。"),
                    nonNegative(
                            request.weeklyToolCalls(),
                            "ai-quota-plan-tool-calls-invalid",
                            "周工具调用次数不能为负数。"),
                    nonNegative(
                            request.fiveHourMcpCalls(),
                            "ai-quota-plan-mcp-calls-invalid",
                            "5 小时 MCP 调用次数不能为负数。"),
                    nonNegative(
                            request.weeklyMcpCalls(),
                            "ai-quota-plan-mcp-calls-invalid",
                            "周 MCP 调用次数不能为负数。"),
                    windowSeconds(request.fiveHourWindowSeconds()),
                    weeklyDays(request.weeklyWindowDays()),
                    anchorMode(request.weeklyAnchorMode()),
                    AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    now,
                    now);
            return plans.insert(plan);
        });
    }

    public Mono<AiQuotaPlan> update(UUID id, AiQuotaPlanUpdateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> get(id).flatMap(existing -> {
            var plan = new AiQuotaPlan(
                    existing.id(),
                    StringUtils.hasText(request.name())
                            ? request.name().trim()
                            : existing.name(),
                    StringUtils.hasText(request.displayName())
                            ? request.displayName().trim()
                            : existing.displayName(),
                    request.fiveHourCredits() == null
                            ? existing.fiveHourCredits()
                            : nonNegative(
                                    request.fiveHourCredits(),
                                    "ai-quota-plan-credits-invalid",
                                    "5 小时积分不能为负数。"),
                    request.weeklyCredits() == null
                            ? existing.weeklyCredits()
                            : nonNegative(
                                    request.weeklyCredits(),
                                    "ai-quota-plan-credits-invalid",
                                    "周积分不能为负数。"),
                    request.fiveHourToolCalls() == null
                            ? existing.fiveHourToolCalls()
                            : nonNegative(
                                    request.fiveHourToolCalls(),
                                    "ai-quota-plan-tool-calls-invalid",
                                    "5 小时工具调用次数不能为负数。"),
                    request.weeklyToolCalls() == null
                            ? existing.weeklyToolCalls()
                            : nonNegative(
                                    request.weeklyToolCalls(),
                                    "ai-quota-plan-tool-calls-invalid",
                                    "周工具调用次数不能为负数。"),
                    request.fiveHourMcpCalls() == null
                            ? existing.fiveHourMcpCalls()
                            : nonNegative(
                                    request.fiveHourMcpCalls(),
                                    "ai-quota-plan-mcp-calls-invalid",
                                    "5 小时 MCP 调用次数不能为负数。"),
                    request.weeklyMcpCalls() == null
                            ? existing.weeklyMcpCalls()
                            : nonNegative(
                                    request.weeklyMcpCalls(),
                                    "ai-quota-plan-mcp-calls-invalid",
                                    "周 MCP 调用次数不能为负数。"),
                    request.fiveHourWindowSeconds() == null
                            ? existing.fiveHourWindowSeconds()
                            : windowSeconds(request.fiveHourWindowSeconds()),
                    request.weeklyWindowDays() == null
                            ? existing.weeklyWindowDays()
                            : weeklyDays(request.weeklyWindowDays()),
                    request.weeklyAnchorMode() == null
                            ? existing.weeklyAnchorMode()
                            : anchorMode(request.weeklyAnchorMode()),
                    request.status() == null
                            ? existing.status()
                            : AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    existing.createdAt(),
                    Instant.now(clock));
            return plans.update(plan);
        }));
    }

    public Mono<Void> delete(UUID id) {
        return get(id)
                .flatMap(plan -> Mono.zip(
                                assignments.existsByPlanId(id),
                                reservations.existsByPlanId(id))
                        .flatMap(inUse -> Boolean.TRUE.equals(inUse.getT1())
                                        || Boolean.TRUE.equals(inUse.getT2())
                                ? Mono.error(new ApiException(
                                        HttpStatus.CONFLICT,
                                        "ai-quota-plan-in-use",
                                        "配额套餐仍被分配或预扣记录引用，不能删除。"))
                                : plans.delete(id)))
                .onErrorMap(
                        DataIntegrityViolationException.class,
                        error -> new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-quota-plan-in-use",
                                "配额套餐仍被引用，不能删除。",
                                error));
    }

    private static BigDecimal nonNegative(
            BigDecimal value, String code, String message) {
        return AiSupport.nonNegative(
                value == null ? BigDecimal.ZERO : value,
                BigDecimal.ZERO,
                code,
                message);
    }

    private static int nonNegative(Integer value, String code, String message) {
        return AiSupport.nonNegative(
                value == null ? 0 : value, code, message);
    }

    private static int windowSeconds(Integer value) {
        var resolved = value == null ? FIVE_HOUR_SECONDS : value;
        if (resolved != FIVE_HOUR_SECONDS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-plan-window-invalid",
                    "5 小时窗口固定为 18000 秒。");
        }
        return resolved;
    }

    private static int weeklyDays(Integer value) {
        var resolved = value == null ? WEEKLY_DAYS : value;
        if (resolved != WEEKLY_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-plan-window-invalid",
                    "周窗口固定为 7 天。");
        }
        return resolved;
    }

    private static String anchorMode(String value) {
        var resolved = StringUtils.hasText(value)
                ? value.trim()
                : WEEKLY_ANCHOR_MODE;
        if (!WEEKLY_ANCHOR_MODE.equals(resolved)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-plan-anchor-invalid",
                    "周窗口锚点模式首期只支持 assignment_start。");
        }
        return resolved;
    }

    private static ApiException bodyRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "ai-quota-plan-body-required",
                "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
