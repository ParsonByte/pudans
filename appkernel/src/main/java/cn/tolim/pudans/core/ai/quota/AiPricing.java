package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiPeakWindow;
import cn.tolim.pudans.contract.ai.AiPriceRule;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiPriceRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiPricing {

    private static final BigDecimal TOKEN_DIVISOR = BigDecimal.valueOf(10_000);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int CREDIT_SCALE = 4;

    private final AiPriceRuleRepository rules;

    private Clock clock = Clock.systemUTC();

    public Mono<PriceQuote> quoteTokens(UUID modelId, TokenUsage usage, Instant at) {
        var effectiveAt = at == null ? Instant.now(clock) : at;
        return rules.findEffectiveModel(modelId, effectiveAt)
                .switchIfEmpty(Mono.error(priceMissing()))
                .map(rule -> quoteTokens(rule, usage, effectiveAt));
    }

    public Mono<PriceQuote> quoteUnits(UUID modelId, BigDecimal units, Instant at) {
        var effectiveAt = at == null ? Instant.now(clock) : at;
        var resolvedUnits = nonNegative(units);
        return rules.findEffectiveModel(modelId, effectiveAt)
                .switchIfEmpty(Mono.error(priceMissing()))
                .map(rule -> quoteUnits(rule, resolvedUnits, effectiveAt));
    }

    public Mono<PriceQuote> quoteMcp(String toolKey, int calls, Instant at) {
        var resolvedKey = StringUtils.hasText(toolKey) ? toolKey.trim() : "";
        if (resolvedKey.isEmpty()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-mcp-tool-key-required",
                    "MCP 工具键不能为空。"));
        }
        if (calls < 0) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-mcp-calls-invalid",
                    "MCP 调用次数不能为负数。"));
        }
        var effectiveAt = at == null ? Instant.now(clock) : at;
        return rules.findEffectiveMcp(resolvedKey, effectiveAt)
                .switchIfEmpty(Mono.error(priceMissing()))
                .map(rule -> quoteMcp(rule, calls, effectiveAt));
    }

    static PriceQuote quoteTokens(AiPriceRule rule, TokenUsage usage, Instant at) {
        if (!"token".equals(rule.pricingMode())) {
            throw modeMismatch("token");
        }
        var input = nonNegative(usage.inputTokens());
        var cached = nonNegative(usage.cachedInputTokens());
        var output = nonNegative(usage.outputTokens());
        var base = input.multiply(rule.inputCoefficient())
                .add(cached.multiply(rule.cachedInputCoefficient()))
                .add(output.multiply(rule.outputCoefficient()))
                .divide(TOKEN_DIVISOR, CREDIT_SCALE + 4, RoundingMode.HALF_UP);
        return applyMultiplier(rule, base, at);
    }

    static PriceQuote quoteUnits(AiPriceRule rule, BigDecimal units, Instant at) {
        if (!"unit_credit".equals(rule.pricingMode())) {
            throw modeMismatch("unit_credit");
        }
        if (!StringUtils.hasText(rule.unit())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ai-price-rule-unit-missing",
                    "单位积分价格规则缺少计量单位。");
        }
        var base = rule.baseCredits()
                .add(nonNegative(units).multiply(rule.unitCredits()));
        return applyMultiplier(rule, base, at);
    }

    static PriceQuote quoteMcp(AiPriceRule rule, int calls, Instant at) {
        if (!"mcp_call".equals(rule.pricingMode())) {
            throw modeMismatch("mcp_call");
        }
        if (calls < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-mcp-calls-invalid",
                    "MCP 调用次数不能为负数。");
        }
        var base = BigDecimal.valueOf(calls).multiply(rule.mcpCallCoefficient());
        return applyMultiplier(rule, base, at);
    }

    static boolean inPeakWindow(List<AiPeakWindow> windows, Instant at) {
        if (windows == null || windows.isEmpty()) {
            return false;
        }
        for (var window : windows) {
            if (matches(window, at)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(AiPeakWindow window, Instant at) {
        if (window == null) {
            return false;
        }
        var zone = StringUtils.hasText(window.zone())
                ? ZoneId.of(window.zone().trim())
                : DEFAULT_ZONE;
        var local = at.atZone(zone);
        if (!dayMatches(window.days(), local.getDayOfWeek())) {
            return false;
        }
        if (!StringUtils.hasText(window.start()) || !StringUtils.hasText(window.end())) {
            return false;
        }
        var start = LocalTime.parse(window.start().trim());
        var end = LocalTime.parse(window.end().trim());
        var time = local.toLocalTime();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    private static boolean dayMatches(List<String> days, DayOfWeek day) {
        if (days == null || days.isEmpty()) {
            return false;
        }
        var shortName = day.name().substring(0, 3).toLowerCase(Locale.ROOT);
        var fullName = day.name().toLowerCase(Locale.ROOT);
        var isoValue = Integer.toString(day.getValue());
        for (var value : days) {
            if (value == null) {
                continue;
            }
            var normalized = value.trim().toLowerCase(Locale.ROOT);
            if (shortName.equals(normalized)
                    || fullName.equals(normalized)
                    || isoValue.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static PriceQuote applyMultiplier(AiPriceRule rule, BigDecimal base, Instant at) {
        var peak = inPeakWindow(rule.peakWindows(), at);
        var multiplier = peak ? rule.peakMultiplier() : rule.offpeakMultiplier();
        var credits = base.multiply(multiplier)
                .setScale(CREDIT_SCALE, RoundingMode.HALF_UP);
        var minimum = rule.minimumCredits() == null
                ? BigDecimal.ZERO
                : rule.minimumCredits();
        if (credits.compareTo(minimum) < 0) {
            credits = minimum.setScale(CREDIT_SCALE, RoundingMode.HALF_UP);
        }
        return new PriceQuote(
                rule.id(),
                rule.pricingMode(),
                credits,
                multiplier,
                peak);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        var resolved = value == null ? BigDecimal.ZERO : value;
        if (resolved.signum() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-usage-negative",
                    "用量不能为负数。");
        }
        return resolved;
    }

    static ApiException priceMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ai-price-rule-missing",
                "当前时间没有可用的价格规则。");
    }

    private static ApiException modeMismatch(String expected) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "ai-price-rule-mode-mismatch",
                "价格规则计价方式必须为 " + expected + "。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record TokenUsage(
            BigDecimal inputTokens,
            BigDecimal cachedInputTokens,
            BigDecimal outputTokens) {}

    public record PriceQuote(
            UUID pricingRuleId,
            String pricingMode,
            BigDecimal credits,
            BigDecimal multiplier,
            boolean peak) {}
}
