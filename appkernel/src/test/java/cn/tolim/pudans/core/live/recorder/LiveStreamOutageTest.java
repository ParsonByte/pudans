// 校验断流计时、告警阈值边界、恢复摘要与恢复告警的配对条件

package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveStreamOutageTest {

    private static final Duration THRESHOLD = Duration.ofSeconds(60);

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final String CODE = "LIVE_STREAM_FAILED";

    private static final String MESSAGE = "拉流中断";

    private final LiveStreamOutage outage = new LiveStreamOutage();

    @Test
    void alertsOnlyOnceAfterThreshold() {
        outage.begin(START, CODE, MESSAGE);

        // 阈值取等号：59 秒不告警，满 60 秒才告警
        assertThat(outage.alertDue(START.plusSeconds(59), THRESHOLD)).isFalse();
        assertThat(outage.alertDue(START.plusSeconds(60), THRESHOLD)).isTrue();

        outage.markAlerted();
        assertThat(outage.alertDue(START.plusSeconds(120), THRESHOLD)).isFalse();
    }

    @Test
    void keepsFirstStartAndLatestReason() {
        outage.begin(START, "LIVE_STREAM_FAILED", "第一次失败");
        outage.begin(START.plusSeconds(30), CODE, MESSAGE);

        var recovered = outage.end(START.plusSeconds(40));
        assertThat(recovered).isNotNull();
        assertThat(recovered.startedAt()).isEqualTo(START);
        assertThat(recovered.duration()).isEqualTo(Duration.ofSeconds(40));
        assertThat(recovered.errorCode()).isEqualTo(CODE);
        assertThat(recovered.message()).isEqualTo(MESSAGE);
    }

    @Test
    void endCarriesAlertFlagAndResets() {
        outage.begin(START, CODE, MESSAGE);
        outage.markAlerted();

        var recovered = outage.end(START.plusSeconds(90));
        assertThat(recovered).isNotNull();
        assertThat(recovered.alerted()).isTrue();

        assertThat(outage.end(START.plusSeconds(100))).isNull();
        assertThat(outage.startedAt()).isNull();
        assertThat(outage.alertDue(START.plusSeconds(100), THRESHOLD)).isFalse();
    }

    @Test
    void shortOutageEndsWithoutAlert() {
        outage.begin(START, CODE, MESSAGE);

        var recovered = outage.end(START.plusSeconds(5));
        assertThat(recovered).isNotNull();
        assertThat(recovered.alerted()).isFalse();
    }

    @Test
    void endWithoutOutageReturnsNull() {
        assertThat(outage.end(START)).isNull();
    }
}
