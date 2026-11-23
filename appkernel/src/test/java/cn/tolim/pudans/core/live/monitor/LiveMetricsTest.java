// 校验录制指标读数与失败计数集合的增删幂等

package cn.tolim.pudans.core.live.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMetricsTest {

    private SimpleMeterRegistry registry;

    private LiveMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        var recorderRegistry = mock(LiveRecorderRegistry.class);
        when(recorderRegistry.activeCount()).thenReturn(2);
        when(recorderRegistry.queuedCount()).thenReturn(3);
        metrics = new LiveMetrics(registry, recorderRegistry);
        metrics.registerGauges();
    }

    @Test
    void recordingGaugesReadFromRegistry() {
        assertThat(registry.get("live.recordings.active").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("live.recordings.queued").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void failedGaugeCountsEachRecordingOnce() {
        // 同一录制重复进入失败状态不应被重复计数
        metrics.recordingFailed("rec-a");
        metrics.recordingFailed("rec-a");
        metrics.recordingFailed("rec-b");

        assertThat(registry.get("live.recordings.failed").gauge().value()).isEqualTo(2.0);

        metrics.recordingRemoved("rec-a");
        assertThat(registry.get("live.recordings.failed").gauge().value()).isEqualTo(1.0);

        metrics.recordingRemoved("rec-b");
        assertThat(registry.get("live.recordings.failed").gauge().value()).isZero();
    }
}
