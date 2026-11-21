package cn.tolim.pudans.extension.contract.index.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetweenConditionTest {

    @Test
    void notKeepsTheInclusiveFlagsOfTheOriginalInterval() {
        var negated = (NotBetweenCondition) new BetweenCondition("stepIndex", 2, false, 4, true).not();

        assertThat(negated.fromInclusive()).isFalse();
        assertThat(negated.toInclusive()).isTrue();
    }

    @Test
    void doubleNegationRestoresTheOriginalInterval() {
        var between = new BetweenCondition("stepIndex", 2, true, 4, false);

        assertThat(between.not().not()).isEqualTo(between);
    }
}
