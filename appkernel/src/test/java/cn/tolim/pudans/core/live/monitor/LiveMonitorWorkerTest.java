package cn.tolim.pudans.core.live.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.resource.LivePlatform;
import cn.tolim.pudans.contract.live.resource.LiveRecording;
import cn.tolim.pudans.contract.live.resource.LiveSource;
import cn.tolim.pudans.contract.live.resource.LiveSourceState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.live.resolve.LiveResolveCache;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.source.LiveSourceRegistry;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LiveMonitorWorkerTest {

    @Mock
    LiveSourceRegistry registry;

    @Mock
    SourceStatus statusService;

    @Mock
    RoomResolver resolveService;

    @Mock
    Recordings recordingService;

    @Mock
    LiveResolveCache resolveCache;

    @Mock
    LiveAlerts alertService;

    @Mock
    LiveMetrics metrics;

    @Mock
    LiveLeaderLease lease;

    private LiveMonitorWorker worker() {
        return new LiveMonitorWorker(
                new LiveProperties(),
                registry,
                statusService,
                resolveService,
                recordingService,
                resolveCache,
                alertService,
                metrics,
                lease);
    }

    private static LiveSourceRegistry.Snapshot snapshot(boolean autoRecord) {
        return new LiveSourceRegistry.Snapshot(
                "douyin-123456789",
                LivePlatform.DOUYIN,
                "123456789",
                LiveSourceState.MONITORING,
                false,
                false,
                autoRecord,
                60,
                Instant.now(),
                null);
    }

    private static LiveRoomInfo liveInfo() {
        return new LiveRoomInfo(
                "123456789", "标题", "主播", null, null, true, "live", List.of(), Instant.now());
    }

    private static LiveSource source(boolean autoRecord) {
        var source = new LiveSource();
        var metadata = new Metadata();
        metadata.setName("douyin-123456789");
        source.setMetadata(metadata);
        source.getSpec().setAutoRecord(autoRecord);
        return source;
    }

    private static LiveRecording recording(String name) {
        var recording = new LiveRecording();
        var metadata = new Metadata();
        metadata.setName(name);
        recording.setMetadata(metadata);
        return recording;
    }

    @Test
    void skipsTickWhenAnotherInstanceHoldsLeaderLease() {
        when(lease.renew()).thenReturn(Mono.just(false));

        StepVerifier.create(worker().tick()).verifyComplete();

        verify(registry, never()).snapshotDue(any(), anyInt());
        verifyNoInteractions(resolveService, recordingService, alertService);
    }

    @Test
    void resolvesDueSourceAndStartsAutomaticRecording() {
        var snapshot = snapshot(true);
        var info = liveInfo();
        var liveSource = source(true);
        when(lease.renew()).thenReturn(Mono.just(true));
        when(registry.snapshotDue(any(), anyInt())).thenReturn(List.of(snapshot));
        when(resolveService.resolve(LivePlatform.DOUYIN, "123456789", true))
                .thenReturn(Mono.just(info));
        when(statusService.applyLive("douyin-123456789", info)).thenReturn(Mono.just(liveSource));
        when(recordingService.start(liveSource, info, true, "monitor"))
                .thenReturn(Mono.just(recording("recording-1")));
        when(statusService.markRecording("douyin-123456789", "recording-1"))
                .thenReturn(Mono.just(liveSource));

        StepVerifier.create(worker().tick()).verifyComplete();

        verify(registry).release("douyin-123456789");
        verify(statusService).applyLive("douyin-123456789", info);
        verify(recordingService).start(liveSource, info, true, "monitor");
        verify(statusService).markRecording("douyin-123456789", "recording-1");
        verify(metrics).monitorTick(any(Duration.class));
    }

    @Test
    void doesNotStartRecordingWhenAutoRecordDisabled() {
        var snapshot = snapshot(false);
        var info = liveInfo();
        var liveSource = source(false);
        when(lease.renew()).thenReturn(Mono.just(true));
        when(registry.snapshotDue(any(), anyInt())).thenReturn(List.of(snapshot));
        when(resolveService.resolve(LivePlatform.DOUYIN, "123456789", true))
                .thenReturn(Mono.just(info));
        when(statusService.applyLive("douyin-123456789", info)).thenReturn(Mono.just(liveSource));

        StepVerifier.create(worker().tick()).verifyComplete();

        verify(registry).release("douyin-123456789");
        verifyNoInteractions(recordingService);
    }
}
