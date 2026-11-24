// 校验录制指标读数与共享失败集合的增删

package cn.tolim.pudans.core.live.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveMetricsTest {

    private static final String FAILED_KEY = "pudans:live:recordings:failed";

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveSetOperations<String, String> setOperations;

    @Mock
    LiveRecorderRegistry recorderRegistry;

    private SimpleMeterRegistry registry;

    private LiveMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        metrics = new LiveMetrics(registry, recorderRegistry, redisTemplate);
        metrics.registerGauges();
    }

    @Test
    void recordingGaugesReadFromRegistry() {
        when(recorderRegistry.activeCount()).thenReturn(2);
        when(recorderRegistry.queuedCount()).thenReturn(3);

        assertThat(registry.get("live.recordings.active").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("live.recordings.queued").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void failedGaugeReadsSharedCountOnLeader() {
        when(setOperations.size(FAILED_KEY)).thenReturn(Mono.just(2L));

        metrics.refreshFailedCount(true).block();

        assertThat(registry.get("live.recordings.failed").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void failedGaugeIsZeroOnFollower() {
        stubSharedAdd();
        metrics.recordingFailed("rec-a");

        metrics.refreshFailedCount(false).block();

        assertThat(registry.get("live.recordings.failed").gauge().value()).isZero();
    }

    @Test
    void recordingFailureWritesAndRemovalDeletesSharedEntry() {
        stubSharedAdd();
        stubSharedRemove();

        metrics.recordingFailed("rec-a");
        verify(setOperations).add(FAILED_KEY, "rec-a");

        metrics.recordingRemoved("rec-a");
        verify(setOperations).remove(FAILED_KEY, "rec-a");
    }

    @Test
    void fallsBackToLocalCountWhenSharedReadFails() {
        stubSharedAdd();
        when(setOperations.size(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));

        metrics.recordingFailed("rec-a");
        metrics.refreshFailedCount(true).block();

        assertThat(registry.get("live.recordings.failed").gauge().value()).isEqualTo(1.0);
    }

    private void stubSharedAdd() {
        when(setOperations.add(anyString(), any())).thenReturn(Mono.just(1L));
    }

    private void stubSharedRemove() {
        when(setOperations.remove(anyString(), any())).thenReturn(Mono.just(1L));
    }
}
