package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveRecordingCleanupTaskTest {

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private StorageTransfer storageTransfer;

    @Mock
    private RecordingStatus recordingStatus;

    @Mock
    private LiveRecordingWorkspace workspace;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private LiveMetrics metrics;

    @Mock
    private LiveAlerts liveAlerts;

    @TempDir
    private Path tempDir;

    private LiveProperties properties;

    private LiveRecordingCleanupTask task;

    @BeforeEach
    void setUp() {
        properties = new LiveProperties();
        properties.getRecorder().setWorkDir(tempDir.toString());
        task = new LiveRecordingCleanupTask(
                client, storageTransfer, recordingStatus, workspace, redisTemplate, properties, metrics, liveAlerts);
        // 默认让删除返回可订阅结果，成功用例再覆盖为可观测的 Mono
        lenient().when(client.delete(any(LiveRecording.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void deletesExpiredCompletedRecording() {
        var recording = recording("live-a", LiveRecordingState.COMPLETED, Instant.now().minus(Duration.ofDays(30)));
        recording.getStatus().setEndedAt(Instant.now().minus(Duration.ofDays(10)));
        var part = part(1, PartState.UPLOADED, "storage-1");
        recording.getStatus().setParts(List.of(part));
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));
        when(storageTransfer.purgeObjectFirst("storage-1")).thenReturn(Mono.empty());
        when(workspace.recordingDir("live-a")).thenReturn(tempDir.resolve("live-a"));
        // 只有 Mono 被订阅才会置位，用于证明删除请求真的执行
        var subscribed = new AtomicBoolean();
        when(client.delete(recording)).thenReturn(Mono.fromRunnable(() -> subscribed.set(true)).thenReturn(recording));

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isEqualTo(1);
        // 未订阅时标记不会置位，删除就会像旧代码一样被丢弃
        assertThat(subscribed).isTrue();
        verify(storageTransfer).purgeObjectFirst("storage-1");
        verify(client).delete(recording);
        // 删除成功后才从失败仪表移除
        verify(metrics).recordingRemoved("live-a");
    }

    @Test
    void keepsRecordingInsideRetentionWindow() {
        var recording = recording("live-b", LiveRecordingState.COMPLETED, Instant.now().minus(Duration.ofDays(30)));
        recording.getStatus().setEndedAt(Instant.now());
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(recording));

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isZero();
        verify(client, never()).delete(any(LiveRecording.class));
    }

    @Test
    void retentionDisabledSkipsQuery() {
        properties.getRetention().setDays(0);

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isZero();
        verifyNoInteractions(client);
    }

    @Test
    void failedCleanupRetriesAndDoesNotBlockFollowingRecords() {
        var failing = recording("live-c", LiveRecordingState.FAILED, Instant.now().minus(Duration.ofDays(30)));
        failing.getStatus().setEndedAt(Instant.now().minus(Duration.ofDays(9)));
        failing.getStatus().setParts(List.of(part(1, PartState.UPLOADED, "storage-c")));
        var succeeding = recording("live-d", LiveRecordingState.FAILED, Instant.now().minus(Duration.ofDays(30)));
        succeeding.getStatus().setEndedAt(Instant.now().minus(Duration.ofDays(8)));
        when(client.listAll(eq(LiveRecording.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(failing, succeeding));
        when(storageTransfer.purgeObjectFirst("storage-c"))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, "storage-object-delete-failed", "删除存储对象失败")));
        when(recordingStatus.mutate(eq("live-c"), any())).thenReturn(Mono.just(failing));
        when(liveAlerts.alert(eq("live.cleanup.failed"), eq("live-c"), any())).thenReturn(Mono.empty());
        when(workspace.recordingDir(anyString())).thenAnswer(invocation -> tempDir.resolve((String) invocation.getArgument(0)));

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isEqualTo(1);
        verify(metrics).cleanupFailed();
        // 失败记录保留计数，成功记录才移除计数
        verify(metrics, never()).recordingRemoved("live-c");
        verify(metrics).recordingRemoved("live-d");
        verify(client, never()).delete(failing);
        verify(client).delete(succeeding);
        var mutation = ArgumentCaptor.forClass(Consumer.class);
        verify(recordingStatus).mutate(eq("live-c"), mutation.capture());
        var status = failing.getStatus();
        status.setCleanupAttempts(0);
        mutation.getValue().accept(status);
        assertThat(status.getCleanupAttempts()).isEqualTo(1);
    }

    private static LiveRecording recording(String name, LiveRecordingState state, Instant startedAt) {
        var recording = new LiveRecording();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setCreationTimestamp(startedAt);
        recording.setMetadata(metadata);
        recording.getSpec().setStartedAt(startedAt);
        recording.getStatus().setState(state);
        return recording;
    }

    private static LiveRecording.Part part(int index, PartState state, String storageFile) {
        var part = new LiveRecording.Part();
        part.setIndex(index);
        part.setState(state);
        part.setStorageFile(storageFile);
        part.setSize(1024);
        return part;
    }
}