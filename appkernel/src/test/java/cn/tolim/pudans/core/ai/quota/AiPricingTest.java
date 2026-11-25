package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiPeakWindow;
import cn.tolim.pudans.contract.ai.AiPriceRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiPricingTest {

    private static final Instant PEAK_TIME = Instant.parse("2026-11-25T06:30:00Z");

    private static final Instant OFFPEAK_TIME = Instant.parse("2026-11-25T02:30:00Z");

    @Test
    void pricesInputCachedAndOutputTokensAtThePeakMultiplier() {
        var rule = tokenRule(new BigDecimal("1.5"), new BigDecimal("0.5"));

        var quote = AiPricing.quoteTokens(
                rule,
                new AiPricing.TokenUsage(
                        new BigDecimal("12000"),
                        new BigDecimal("4000"),
                        new BigDecimal("3000")),
                PEAK_TIME);

        assertThat(quote.credits()).isEqualByComparingTo("6.0000");
        assertThat(quote.peak()).isTrue();
        assertThat(quote.multiplier()).isEqualByComparingTo("1.5");
    }

    @Test
    void pricesTokensAtTheOffpeakMultiplierOutsideConfiguredWindows() {
        var rule = tokenRule(new BigDecimal("1.5"), new BigDecimal("0.5"));

        var quote = AiPricing.quoteTokens(
                rule,
                new AiPricing.TokenUsage(
                        new BigDecimal("12000"),
                        new BigDecimal("4000"),
                        new BigDecimal("3000")),
                OFFPEAK_TIME);

        assertThat(quote.credits()).isEqualByComparingTo("2.0000");
        assertThat(quote.peak()).isFalse();
        assertThat(quote.multiplier()).isEqualByComparingTo("0.5");
    }

    @Test
    void pricesUnitCreditsFromBaseAndUnits() {
        var rule = unitRule(BigDecimal.ZERO);

        var quote = AiPricing.quoteUnits(rule, new BigDecimal("2"), OFFPEAK_TIME);

        assertThat(quote.credits()).isEqualByComparingTo("2.5000");
    }

    @Test
    void appliesTheMinimumCreditFloor() {
        var rule = unitRule(new BigDecimal("4"));

        var quote = AiPricing.quoteUnits(rule, new BigDecimal("2"), OFFPEAK_TIME);

        assertThat(quote.credits()).isEqualByComparingTo("4.0000");
    }

    @Test
    void pricesMcpCallsWithThePeakMultiplier() {
        var rule = mcpRule();

        var quote = AiPricing.quoteMcp(rule, 3, PEAK_TIME);

        assertThat(quote.credits()).isEqualByComparingTo("9.0000");
        assertThat(quote.peak()).isTrue();
    }

    private static AiPriceRule tokenRule(
            BigDecimal peakMultiplier, BigDecimal offpeakMultiplier) {
        return new AiPriceRule(
                UUID.randomUUID(),
                "model",
                "test-model",
                UUID.randomUUID(),
                null,
                "token",
                new BigDecimal("2"),
                new BigDecimal("1"),
                new BigDecimal("4"),
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                peakWindows(),
                peakMultiplier,
                offpeakMultiplier,
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static AiPriceRule unitRule(BigDecimal minimum) {
        return new AiPriceRule(
                UUID.randomUUID(),
                "model",
                "test-media",
                UUID.randomUUID(),
                null,
                "unit_credit",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "image",
                new BigDecimal("2"),
                new BigDecimal("1"),
                minimum,
                peakWindows(),
                BigDecimal.ONE,
                new BigDecimal("0.5"),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static AiPriceRule mcpRule() {
        return new AiPriceRule(
                UUID.randomUUID(),
                "mcp_tool",
                "test-tool",
                null,
                "test-tool",
                "mcp_call",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("2"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                peakWindows(),
                new BigDecimal("1.5"),
                new BigDecimal("0.5"),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                null,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static List<AiPeakWindow> peakWindows() {
        return List.of(new AiPeakWindow(
                List.of("wed"),
                "14:00",
                "18:00",
                "Asia/Shanghai"));
    }
}
