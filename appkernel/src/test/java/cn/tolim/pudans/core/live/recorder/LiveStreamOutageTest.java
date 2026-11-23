// 校验断流计时、告警阈值边界与恢复时长计算

package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveStreamOutageTest {

    private static final Duration THRESHOLD = Duration.ofSeconds(60);

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final LiveStreamOutage outage = new LiveStreamOutage();

    @Test
    void alertsOnlyOnceAfterThreshold() {
        outage.begin(START);

        // 阈值取等号：59 秒不告警，满 60 秒才告警
        assertThat(outage.alertDue(START.plusSeconds(59), THRESHOLD)).isFalse();
        assertThat(outage.alertDue(START.plusSeconds(60), THRESHOLD)).isTrue();

        outage.markAlerted();
        assertThat(outage.alertDue(START.plusSeconds(120), THRESHOLD)).isFalse();
    }

    @Test
    void keepsFirstStartWhenBeginRepeats() {
        outage.begin(START);
        outage.begin(START.plusSeconds(30));

        assertThat(outage.end(START.plusSeconds(40))).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void endReturnsDurationAndResets() {
        outage.begin(START);
        outage.markAlerted();

        assertThat(outage.end(START.plusSeconds(90))).isEqualTo(Duration.ofSeconds(90));
        assertThat(outage.end(START.plusSeconds(100))).isNull();
        assertThat(outage.alertDue(START.plusSeconds(100), THRESHOLD)).isFalse();
    }

    @Test
    void endWithoutOutageReturnsNull() {
        assertThat(outage.end(START)).isNull();
    }
}
