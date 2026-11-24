// 校验录制任务的断流告警阈值、字段载荷与恢复告警配对

package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

class LiveRecorderTaskAlertTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final String MESSAGE = "拉流中断";

    private final LiveProperties properties = new LiveProperties();

    private final LiveAlerts liveAlerts = mock(LiveAlerts.class);

    private final LiveRecorderTask task = task();

    @Test
    void shortInterruptionPublishesNoAlert() {
        task.recordInterruption(START, LiveErrorCode.LIVE_STREAM_FAILED, MESSAGE);
        task.notifyRecovered(START.plusSeconds(5));

        verifyNoInteractions(liveAlerts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void alertsOnceAtThresholdAndPairsRecovery() {
        when(liveAlerts.alertAlways(anyString(), anyString(), anyMap())).thenReturn(Mono.empty());

        task.recordInterruption(START, LiveErrorCode.LIVE_STREAM_FAILED, "第一次失败");
        task.recordInterruption(START.plusSeconds(61), LiveErrorCode.LIVE_STREAM_FAILED, MESSAGE);
        task.recordInterruption(START.plusSeconds(62), LiveErrorCode.LIVE_STREAM_FAILED, MESSAGE);

        var interrupted = ArgumentCaptor.forClass(Map.class);
        verify(liveAlerts).alertAlways(eq("live.stream.interrupted"), eq("rec-1"), interrupted.capture());
        assertThat(interrupted.getValue())
                .containsEntry("recordingName", "rec-1")
                .containsEntry("sourceName", "src-1")
                .containsEntry("roomId", "123456")
                .containsEntry("startedAt", START.toString())
                .containsEntry("at", START.plusSeconds(61).toString())
                .containsEntry("errorCode", "LIVE_STREAM_FAILED")
                .containsEntry("message", MESSAGE)
                .doesNotContainKey("durationMillis");

        task.notifyRecovered(START.plusSeconds(120));

        var recovered = ArgumentCaptor.forClass(Map.class);
        verify(liveAlerts).alertAlways(eq("live.stream.recovered"), eq("rec-1"), recovered.capture());
        assertThat(recovered.getValue())
                .containsEntry("recordingName", "rec-1")
                .containsEntry("startedAt", START.toString())
                .containsEntry("at", START.plusSeconds(120).toString())
                .containsEntry("errorCode", "LIVE_STREAM_FAILED")
                .containsEntry("message", MESSAGE)
                .containsEntry("durationMillis", 120_000L);
    }

    // 构造只依赖录制参数的告警测试任务
    private LiveRecorderTask task() {
        var recording = new LiveRecording();
        recording.setMetadata(new Metadata());
        recording.getMetadata().setName("rec-1");
        recording.getSpec().setSourceName("src-1");
        recording.getSpec().setPlatform(LivePlatform.DOUYIN);
        recording.getSpec().setRoomId("123456");
        return new LiveRecorderTask(
                properties,
                mock(RecordingStatus.class),
                mock(LiveRecorderRegistry.class),
                mock(LivePartMerger.class),
                mock(LiveRecordingWorkspace.class),
                mock(LiveSegmentManifest.class),
                mock(LiveStreamOpener.class),
                mock(RoomResolver.class),
                mock(LiveDiskGuard.class),
                mock(LiveRecorderBackoff.class),
                mock(LiveStoragePolicyResolver.class),
                mock(LiveMetrics.class),
                liveAlerts,
                mock(ReactiveStringRedisTemplate.class),
                mock(SourceStatus.class),
                recording);
    }
}
