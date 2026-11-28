package cn.tolim.pudans.contract.mq;

import static cn.tolim.pudans.contract.mq.MqApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class MqContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(MESSAGES).startsWith(BASE);
        assertThat(MESSAGE_DETAIL).startsWith(BASE);
        assertThat(MESSAGE_DELIVERIES).startsWith(BASE);
        assertThat(MESSAGE_RETRY).startsWith(BASE);
        assertThat(MESSAGE_CANCEL).startsWith(BASE);
        assertThat(DEAD_LETTERS).startsWith(BASE);
        assertThat(DEAD_LETTER_DETAIL).startsWith(BASE);
        assertThat(DEAD_LETTER_REPLAY).startsWith(BASE);
        assertThat(DEAD_LETTER_DISCARD).startsWith(BASE);
        assertThat(DEAD_LETTER_COMPENSATE).startsWith(BASE);
        assertThat(STATS).startsWith(BASE);
        assertThat(CONSUMERS).startsWith(BASE);
        assertThat(DISPATCH).startsWith(BASE);
    }
}