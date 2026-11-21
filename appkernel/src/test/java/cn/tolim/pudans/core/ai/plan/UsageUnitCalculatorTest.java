package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageUnitCalculatorTest {

    @Test
    void appliesTokenWeightsTierAndModelMultipliers() {
        var billing = billing();
        billing.getModelMultipliers().put("model-pro", new BigDecimal("2.0"));
        var calculator = new UsageUnitCalculator(billing, false);

        var units = calculator.charge(
                usage(600_000, 40_000, null, null, null, null, null, null, null),
                AiModel.Tier.HIGH,
                "model-pro");

        assertThat(units).isEqualByComparingTo("4560000");
    }

    @Test
    void estimatesPromptAndCappedOutputTokens() {
        var calculator = new UsageUnitCalculator(billing(), false);

        assertThat(calculator.estimate(1000, 500, AiModel.Tier.MEDIUM, null))
                .isEqualByComparingTo("3000");
        assertThat(calculator.estimate(1000, 10_000, AiModel.Tier.MEDIUM, null))
                .isEqualByComparingTo("33768");
    }

    @Test
    void chargesCacheAndMediaUnits() {
        var calculator = new UsageUnitCalculator(billing(), false);

        var units = calculator.charge(
                usage(1000, null, 100, 0, 2, 3, 2, 1500, 1),
                AiModel.Tier.CHEAP,
                null);

        assertThat(units).isEqualByComparingTo("5263");
    }

    @Test
    void roundsFractionalUnitsUp() {
        var calculator = new UsageUnitCalculator(billing(), false);

        var units = calculator.charge(
                usage(null, null, 1, null, null, null, null, null, null),
                AiModel.Tier.MEDIUM,
                null);

        assertThat(units).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void freeModelsProduceNoUnits() {
        var calculator = new UsageUnitCalculator(billing(), true);

        assertThat(calculator.charge(
                        usage(1_000_000, 1_000_000, null, null, null, null, null, null, null),
                        AiModel.Tier.ULTRA,
                        null))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(calculator.estimate(1_000_000, 1_000_000, AiModel.Tier.ULTRA, null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsNonPositiveWeights() {
        var billing = billing();
        billing.getRatios().setInput(BigDecimal.ZERO);

        assertThatThrownBy(() -> new UsageUnitCalculator(billing, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTierMultipliers() {
        var billing = billing();
        billing.getTierMultipliers().put(AiModel.Tier.HIGH, BigDecimal.ZERO);
        var calculator = new UsageUnitCalculator(billing, false);

        assertThatThrownBy(() -> calculator.estimate(10, 10, AiModel.Tier.HIGH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiSetting.Billing billing() {
        return new AiSetting.Billing();
    }

    private static AiModelUsage usage(
            Integer input,
            Integer output,
            Integer cacheRead,
            Integer cacheWrite,
            Integer images,
            Integer videoSeconds,
            Integer audioSeconds,
            Integer speechCharacters,
            Integer toolCalls) {
        return new AiModelUsage(
                input,
                output,
                cacheRead,
                cacheWrite,
                images,
                videoSeconds,
                audioSeconds,
                speechCharacters,
                toolCalls,
                Map.of());
    }
}