package cn.tolim.pudans.contract.notification;

import static cn.tolim.pudans.contract.notification.NotificationApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class NotificationContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(SEND).startsWith(BASE);
        assertThat(SEND_BATCH).startsWith(BASE);
        assertThat(RECORDS).startsWith(BASE);
        assertThat(RECORD_DETAIL).startsWith(BASE);
        assertThat(RECORD_RETRY).startsWith(BASE);
        assertThat(DEAD_LETTERS).startsWith(BASE);
        assertThat(DEAD_LETTER_DETAIL).startsWith(BASE);
        assertThat(DEAD_LETTER_REPLAY).startsWith(BASE);
        assertThat(STATS).startsWith(BASE);
        assertThat(PROVIDERS).startsWith(BASE);
        assertThat(PROVIDER_DETAIL).startsWith(BASE);
        assertThat(PROVIDER_TEST).startsWith(BASE);
        assertThat(TEMPLATES).startsWith(BASE);
        assertThat(TEMPLATE_DETAIL).startsWith(BASE);
    }
}
