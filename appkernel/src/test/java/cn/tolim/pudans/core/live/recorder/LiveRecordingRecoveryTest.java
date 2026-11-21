package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.reactivestreams.Publisher;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.properties.LiveProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveRecordingRecoveryTest {

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private RecordingStatus recordingStatus;

    @Mock
    private LiveRecorderRegistry registry;

    @Mock
    private LiveRecordingWorkspace workspace;

    @Mock
    private SourceStatus sourceStatus;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @TempDir
    private Path tempDir;

    private LiveRecordingRecovery recovery;

    @BeforeEach
    void setUp() {
        var properties = new LiveProperties();
        properties.getRecorder().setWorkDir(tempDir.toString());
        recovery = new LiveRecordingRecovery(
                client, recordingStatus, registry, workspace, sourceStatus, redisTemplate, properties);
    }

    @Test
    void marksStaleRecordingFailed() {
        var recording = recording("live-stale", LiveRecordingState.RECORDING, Instant.now().minus(Duration.ofHours(2)));
        recording.getStatus().setHeartbeatAt(Instant.now().minus(Duration.ofHours(1)));
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(registry.isActive("live-stale")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));
        when(redisTemplate.delete(any(Publisher.class))).thenReturn(Mono.just(2L));
        when(recordingStatus.fail(eq("live-stale"), eq(LiveErrorCode.LIVE_RECORDER_LOST), anyString()))
                .thenReturn(Mono.just(recording));
        when(workspace.recordingDir("live-stale")).thenReturn(tempDir.resolve("live-stale"));

        var recovered = recovery.recover().block();

        assertThat(recovered).isEqualTo(1);
        verify(recordingStatus).fail(eq("live-stale"), eq(LiveErrorCode.LIVE_RECORDER_LOST), anyString());
        verify(redisTemplate).delete(any(Publisher.class));
    }

    // 恢复失联录制时同步释放直播源指针，避免源一直显示录制中。
    @Test
    void clearsSourceRecordingWhenLostRecordingIsRecovered() {
        var recording = recording(
                "live-source-stale", LiveRecordingState.RECORDING, Instant.now().minus(Duration.ofHours(2)));
        recording.getSpec().setSourceName("source-1");
        recording.getStatus().setHeartbeatAt(Instant.now().minus(Duration.ofHours(1)));
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(registry.isActive("live-source-stale")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));
        when(redisTemplate.delete(any(Publisher.class))).thenReturn(Mono.just(2L));
        when(recordingStatus.fail(
                        eq("live-source-stale"), eq(LiveErrorCode.LIVE_RECORDER_LOST), anyString()))
                .thenReturn(Mono.just(recording));
        when(workspace.recordingDir("live-source-stale")).thenReturn(tempDir.resolve("live-source-stale"));
        when(sourceStatus.recordingEnded("source-1")).thenReturn(Mono.just(new LiveSource()));

        assertThat(recovery.recover().block()).isEqualTo(1);

        verify(sourceStatus).recordingEnded("source-1");
    }

    @Test
    void skipsRecordingWithFreshHeartbeat() {
        var recording = recording("live-fresh", LiveRecordingState.RECORDING, Instant.now().minus(Duration.ofHours(1)));
        recording.getStatus().setHeartbeatAt(Instant.now());
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(registry.isActive("live-fresh")).thenReturn(false);

        var recovered = recovery.recover().block();

        assertThat(recovered).isZero();
        verify(recordingStatus, never()).fail(anyString(), any(LiveErrorCode.class), anyString());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void skipsYoungQueuedRecording() {
        var recording = recording("live-queued", LiveRecordingState.QUEUED, Instant.now());
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(registry.isActive("live-queued")).thenReturn(false);

        var recovered = recovery.recover().block();

        assertThat(recovered).isZero();
        verify(recordingStatus, never()).fail(anyString(), any(LiveErrorCode.class), anyString());
    }

    @Test
    void skipsRecordingRunningInThisInstance() {
        var recording = recording("live-local", LiveRecordingState.RECORDING, Instant.now().minus(Duration.ofHours(1)));
        recording.getStatus().setHeartbeatAt(Instant.now().minus(Duration.ofHours(1)));
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(registry.isActive("live-local")).thenReturn(true);

        var recovered = recovery.recover().block();

        assertThat(recovered).isZero();
        verifyNoInteractions(redisTemplate);
    }

    private static LiveRecording recording(String name, LiveRecordingState state, Instant createdAt) {
        var recording = new LiveRecording();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setCreationTimestamp(createdAt);
        recording.setMetadata(metadata);
        recording.getSpec().setStartedAt(createdAt);
        recording.getStatus().setState(state);
        return recording;
    }
}