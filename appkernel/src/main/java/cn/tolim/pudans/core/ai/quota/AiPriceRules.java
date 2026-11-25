package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiPeakWindow;
import cn.tolim.pudans.contract.ai.AiPriceRule;
import cn.tolim.pudans.contract.ai.dto.AiPriceRuleCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiPriceRuleUpdateRequest;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.core.ai.catalog.AiCapabilityRegistry;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiPriceRuleRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiUsageRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiPriceRules {

    private static final Set<String> TARGET_TYPES = Set.of("model", "mcp_tool");

    private static final Set<String> PRICING_MODES = Set.of("token", "unit_credit", "mcp_call");

    private static final Set<String> UNITS = Set.of(
            "request", "image", "video_second", "audio_second", "megapixel");

    private static final Set<String> DAYS = Set.of(
            "mon", "monday", "1",
            "tue", "tuesday", "2",
            "wed", "wednesday", "3",
            "thu", "thursday", "4",
            "fri", "friday", "5",
            "sat", "saturday", "6",
            "sun", "sunday", "7");

    private final AiPriceRuleRepository rules;

    private final AiModelRepository models;

    private final AiUsageRecordRepository usageRecords;

    private final ObjectProvider<AiCapabilityRegistry> capabilityRegistry;

    private Clock clock = Clock.systemUTC();

    public Flux<AiPriceRule> list() {
        return rules.findAll();
    }

    public Mono<AiPriceRule> get(UUID id) {
        return rules
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-price-rule-not-found", "价格规则不存在。")));
    }

    public Mono<AiPriceRule> create(AiPriceRuleCreateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            return build(
                            null,
                            request.targetType(),
                            request.modelId(),
                            request.mcpToolKey(),
                            request.pricingMode(),
                            request.inputCoefficient(),
                            request.cachedInputCoefficient(),
                            request.outputCoefficient(),
                            request.mcpCallCoefficient(),
                            request.unit(),
                            request.unitCredits(),
                            request.baseCredits(),
                            request.minimumCredits(),
                            request.peakWindows(),
                            request.peakMultiplier(),
                            request.offpeakMultiplier(),
                            request.quotaBucket(),
                            request.effectiveFrom(),
                            request.effectiveTo(),
                            now,
                            now)
                    .flatMap(this::validateTarget)
                    .flatMap(this::validateNoOverlap)
                    .flatMap(rules::insert);
        });
    }

    public Mono<AiPriceRule> update(UUID id, AiPriceRuleUpdateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> get(id).flatMap(existing -> build(
                        existing,
                        request.targetType(),
                        request.modelId(),
                        request.mcpToolKey(),
                        request.pricingMode(),
                        request.inputCoefficient(),
                        request.cachedInputCoefficient(),
                        request.outputCoefficient(),
                        request.mcpCallCoefficient(),
                        request.unit(),
                        request.unitCredits(),
                        request.baseCredits(),
                        request.minimumCredits(),
                        request.peakWindows(),
                        request.peakMultiplier(),
                        request.offpeakMultiplier(),
                        request.quotaBucket(),
                        request.effectiveFrom(),
                        request.effectiveTo(),
                        existing.createdAt(),
                        Instant.now(clock))
                .flatMap(this::validateTarget)
                .flatMap(this::validateNoOverlap)
                .flatMap(rules::update)));
    }

    public Mono<Void> delete(UUID id) {
        return get(id)
                .flatMap(rule -> usageRecords
                        .existsByPricingRuleId(id)
                        .flatMap(inUse -> Boolean.TRUE.equals(inUse)
                                ? Mono.error(new ApiException(
                                        HttpStatus.CONFLICT,
                                        "ai-price-rule-in-use",
                                        "价格规则已被用量流水引用，不能删除。"))
                                : rules.delete(id)))
                .onErrorMap(
                        DataIntegrityViolationException.class,
                        error -> new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-price-rule-in-use",
                                "价格规则仍被用量流水引用，不能删除。",
                                error));
    }

    private Mono<AiPriceRule> build(
            AiPriceRule existing,
            String targetType,
            UUID modelId,
            String mcpToolKey,
            String pricingMode,
            BigDecimal inputCoefficient,
            BigDecimal cachedInputCoefficient,
            BigDecimal outputCoefficient,
            BigDecimal mcpCallCoefficient,
            String unit,
            BigDecimal unitCredits,
            BigDecimal baseCredits,
            BigDecimal minimumCredits,
            List<AiPeakWindow> peakWindows,
            BigDecimal peakMultiplier,
            BigDecimal offpeakMultiplier,
            String quotaBucket,
            Instant effectiveFrom,
            Instant effectiveTo,
            Instant createdAt,
            Instant updatedAt) {
        var resolvedTargetType = targetType == null
                ? existing == null ? null : existing.targetType()
                : targetType.trim().toLowerCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(resolvedTargetType)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-target-invalid",
                    "价格目标类型必须为 model 或 mcp_tool。"));
        }
        var resolvedMode = pricingMode == null
                ? existing == null ? null : existing.pricingMode()
                : pricingMode.trim().toLowerCase(Locale.ROOT);
        if (!PRICING_MODES.contains(resolvedMode)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-mode-invalid",
                    "计价方式必须为 token、unit_credit 或 mcp_call。"));
        }
        var resolvedModelId = modelId == null ? existing == null ? null : existing.modelId() : modelId;
        var resolvedToolKey = mcpToolKey == null
                ? existing == null ? null : existing.mcpToolKey()
                : blankToNull(mcpToolKey);
        if ("model".equals(resolvedTargetType)
                && (resolvedModelId == null || resolvedToolKey != null)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-target-invalid",
                    "模型价格规则必须且只能指定模型。"));
        }
        if ("mcp_tool".equals(resolvedTargetType)
                && (resolvedModelId != null || resolvedToolKey == null)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-target-invalid",
                    "MCP 价格规则必须且只能指定工具键。"));
        }
        if ("mcp_tool".equals(resolvedTargetType) && !"mcp_call".equals(resolvedMode)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-mode-invalid",
                    "MCP 价格规则必须使用 mcp_call 计价。"));
        }
        if ("model".equals(resolvedTargetType) && "mcp_call".equals(resolvedMode)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-mode-invalid",
                    "模型价格规则不能使用 mcp_call 计价。"));
        }
        var resolvedUnit = unit == null
                ? existing == null ? null : existing.unit()
                : blankToNull(unit);
        if ("unit_credit".equals(resolvedMode)) {
            if (!StringUtils.hasText(resolvedUnit) || !UNITS.contains(resolvedUnit)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-price-rule-unit-invalid",
                        "单位积分价格规则缺少受支持的计量单位。"));
            }
        } else {
            resolvedUnit = null;
        }
        var resolvedPeakWindows = peakWindows == null
                ? existing == null ? List.<AiPeakWindow>of() : existing.peakWindows()
                : peakWindows;
        try {
            validatePeakWindows(resolvedPeakWindows);
        } catch (RuntimeException error) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-peak-window-invalid",
                    "高峰时段配置无效：" + error.getMessage(),
                    error));
        }
        var resolvedFrom = effectiveFrom == null
                ? existing == null ? Instant.now(clock) : existing.effectiveFrom()
                : effectiveFrom;
        var resolvedTo = effectiveTo == null
                ? existing == null ? null : existing.effectiveTo()
                : effectiveTo;
        if (resolvedTo != null && !resolvedTo.isAfter(resolvedFrom)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-effective-invalid",
                    "价格规则失效时间必须晚于生效时间。"));
        }
        var resolvedBucket = quotaBucket == null
                ? existing == null ? null : existing.quotaBucket()
                : blankToNull(quotaBucket);
        if (resolvedBucket != null && resolvedBucket.length() > 64) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-price-rule-bucket-invalid",
                    "配额桶名称长度不能超过 64 个字符。"));
        }
        var rule = new AiPriceRule(
                existing == null ? UUID.randomUUID() : existing.id(),
                resolvedTargetType,
                "",
                resolvedModelId,
                resolvedToolKey,
                resolvedMode,
                AiSupport.nonNegative(
                        inputCoefficient,
                        existing == null ? BigDecimal.ZERO : existing.inputCoefficient(),
                        "ai-price-rule-coefficient-invalid",
                        "Token 系数不能为负数。"),
                AiSupport.nonNegative(
                        cachedInputCoefficient,
                        existing == null ? BigDecimal.ZERO : existing.cachedInputCoefficient(),
                        "ai-price-rule-coefficient-invalid",
                        "缓存 Token 系数不能为负数。"),
                AiSupport.nonNegative(
                        outputCoefficient,
                        existing == null ? BigDecimal.ZERO : existing.outputCoefficient(),
                        "ai-price-rule-coefficient-invalid",
                        "输出 Token 系数不能为负数。"),
                AiSupport.nonNegative(
                        mcpCallCoefficient,
                        existing == null ? BigDecimal.ZERO : existing.mcpCallCoefficient(),
                        "ai-price-rule-coefficient-invalid",
                        "MCP 调用系数不能为负数。"),
                resolvedUnit,
                AiSupport.nonNegative(
                        unitCredits,
                        existing == null ? BigDecimal.ZERO : existing.unitCredits(),
                        "ai-price-rule-credits-invalid",
                        "单位积分不能为负数。"),
                AiSupport.nonNegative(
                        baseCredits,
                        existing == null ? BigDecimal.ZERO : existing.baseCredits(),
                        "ai-price-rule-credits-invalid",
                        "基础积分不能为负数。"),
                AiSupport.nonNegative(
                        minimumCredits,
                        existing == null ? BigDecimal.ZERO : existing.minimumCredits(),
                        "ai-price-rule-credits-invalid",
                        "最低积分不能为负数。"),
                List.copyOf(resolvedPeakWindows),
                AiSupport.nonNegative(
                        peakMultiplier,
                        existing == null ? BigDecimal.ONE : existing.peakMultiplier(),
                        "ai-price-rule-multiplier-invalid",
                        "高峰倍率不能为负数。"),
                AiSupport.nonNegative(
                        offpeakMultiplier,
                        existing == null ? BigDecimal.ONE : existing.offpeakMultiplier(),
                        "ai-price-rule-multiplier-invalid",
                        "非高峰倍率不能为负数。"),
                resolvedBucket,
                resolvedFrom,
                resolvedTo,
                createdAt,
                updatedAt);
        return Mono.just(rule);
    }

    private Mono<AiPriceRule> validateTarget(AiPriceRule rule) {
        if ("mcp_tool".equals(rule.targetType())) {
            var registry = capabilityRegistry.getIfAvailable();
            if (registry == null) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-mcp-tool-not-found",
                        "MCP 工具尚未注册。"));
            }
            return registry
                    .tools()
                    .any(tool -> rule.mcpToolKey().equals(tool.key()) && tool.enabled())
                    .flatMap(found -> found
                            ? Mono.just(withTargetKey(rule, rule.mcpToolKey()))
                            : Mono.error(new ApiException(
                                    HttpStatus.CONFLICT,
                                    "ai-mcp-tool-not-found",
                                    "MCP 工具不存在或当前不可用。")));
        }
        return models
                .findById(rule.modelId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-model-not-found", "AI 模型不存在。")))
                .map(AiModel::logicalName)
                .map(logicalName -> withTargetKey(rule, logicalName));
    }

    private Mono<AiPriceRule> validateNoOverlap(AiPriceRule candidate) {
        var existing = "model".equals(candidate.targetType())
                ? rules.findByModelId(candidate.modelId())
                : rules.findByMcpToolKey(candidate.mcpToolKey());
        return existing
                .filter(rule -> !rule.id().equals(candidate.id()))
                .filter(rule -> overlaps(rule, candidate))
                .hasElements()
                .flatMap(overlap -> Boolean.TRUE.equals(overlap)
                        ? Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-price-rule-overlap",
                                "同一目标的生效区间不能重叠。"))
                        : Mono.just(candidate));
    }

    private static boolean overlaps(AiPriceRule existing, AiPriceRule candidate) {
        var existingTo = existing.effectiveTo();
        var candidateTo = candidate.effectiveTo();
        var existingBeforeCandidateEnds = candidateTo == null
                || existing.effectiveFrom().isBefore(candidateTo);
        var candidateBeforeExistingEnds = existingTo == null
                || candidate.effectiveFrom().isBefore(existingTo);
        return existingBeforeCandidateEnds && candidateBeforeExistingEnds;
    }

    private static void validatePeakWindows(List<AiPeakWindow> windows) {
        for (var window : windows) {
            if (window == null || window.days() == null || window.days().isEmpty()) {
                throw new IllegalArgumentException("高峰时段必须包含星期。");
            }
            for (var day : window.days()) {
                if (day == null
                        || !DAYS.contains(day.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("不支持的星期值。");
                }
            }
            LocalTime.parse(window.start());
            LocalTime.parse(window.end());
            if (StringUtils.hasText(window.zone())) {
                ZoneId.of(window.zone().trim());
            }
        }
    }

    private static AiPriceRule withTargetKey(AiPriceRule rule, String targetKey) {
        return new AiPriceRule(
                rule.id(),
                rule.targetType(),
                targetKey,
                rule.modelId(),
                rule.mcpToolKey(),
                rule.pricingMode(),
                rule.inputCoefficient(),
                rule.cachedInputCoefficient(),
                rule.outputCoefficient(),
                rule.mcpCallCoefficient(),
                rule.unit(),
                rule.unitCredits(),
                rule.baseCredits(),
                rule.minimumCredits(),
                rule.peakWindows(),
                rule.peakMultiplier(),
                rule.offpeakMultiplier(),
                rule.quotaBucket(),
                rule.effectiveFrom(),
                rule.effectiveTo(),
                rule.createdAt(),
                rule.updatedAt());
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException bodyRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "ai-price-rule-body-required",
                "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
