package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiPointsCalculatorTest {

    @Test
    void roundsEachTokenSegmentUp() {
        var model = model(1000, 500, 200);

        var points = AiPointsCalculator.charge(model, false, 1001, 501, 201);

        assertThat(points).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void ignoresEmptySegments() {
        var model = model(1000, 1000, 1000);

        var points = AiPointsCalculator.charge(model, false, 0, 0, 999);

        assertThat(points).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void freeProvidersProduceNoCharge() {
        var model = model(1000, 1000, 1000);

        var points = AiPointsCalculator.charge(model, true, 100000, 100000, 100000);

        assertThat(points).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void estimatesFromPromptAndMaxOutputTokens() {
        var model = model(200, 200, 100);

        var points = AiPointsCalculator.estimate(model, false, 401, 201);

        assertThat(points).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void rejectsAnInvalidUnitPrice() {
        var model = model(0, 1000, 1000);

        assertThatThrownBy(() -> AiPointsCalculator.charge(model, false, 10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiModel model(int input, int cache, int output) {
        var model = new AiModel();
        model.getSpec().setModelCode("gpt-test");
        model.getSpec().setInputTokensPerPoint(input);
        model.getSpec().setCacheTokensPerPoint(cache);
        model.getSpec().setOutputTokensPerPoint(output);
        return model;
    }
}
