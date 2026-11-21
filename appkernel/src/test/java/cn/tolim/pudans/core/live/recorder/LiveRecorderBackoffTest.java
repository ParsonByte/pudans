package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LiveRecorderBackoffTest {

    private static LiveProperties properties() {
        var properties = new LiveProperties();
        properties.getRecorder().setRetryBackoff(Duration.ofSeconds(1));
        properties.getRecorder().setMaxRetryBackoff(Duration.ofSeconds(30));
        properties.getRecorder().setRetryJitter(0.0);
        return properties;
    }

    @Test
    void doublesFromOneSecondAndCapsAtMaxBackoff() {
        var backoff = new LiveRecorderBackoff(properties());

        assertThat(backoff.next(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.next(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.next(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.next(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.next(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(backoff.next(6)).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.next(12)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void treatsNonPositiveAttemptAsFirstRetry() {
        var backoff = new LiveRecorderBackoff(properties());

        assertThat(backoff.next(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.next(-3)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void appliesJitterWithinTwentyPercent() {
        var properties = properties();
        properties.getRecorder().setRetryJitter(0.2);
        var backoff = new LiveRecorderBackoff(properties);

        for (var i = 0; i < 50; i++) {
            var millis = backoff.next(3).toMillis();
            assertThat(millis).isBetween(3_200L, 4_800L);
        }
    }
}
