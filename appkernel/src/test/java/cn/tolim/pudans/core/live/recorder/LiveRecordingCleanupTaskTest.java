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
import java.util.List;
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
import cn.tolim.pudans.contract.live.resource.LiveRecording;
import cn.tolim.pudans.contract.live.resource.LiveRecordingState;
import cn.tolim.pudans.contract.live.resource.PartState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
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
    private RecordingStatus statusService;

    @Mock
    private LiveRecordingWorkspace workspace;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private LiveMetrics metrics;

    @Mock
    private LiveAlerts alertService;

    @TempDir
    private Path tempDir;

    private LiveProperties properties;

    private LiveRecordingCleanupTask task;

    @BeforeEach
    void setUp() {
        properties = new LiveProperties();
        properties.getRecorder().setWorkDir(tempDir.toString());
        task = new LiveRecordingCleanupTask(
                client, storageTransfer, statusService, workspace, redisTemplate, properties, metrics, alertService);
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

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isEqualTo(1);
        verify(storageTransfer).purgeObjectFirst("storage-1");
        verify(client).delete(recording);
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
        when(statusService.mutate(eq("live-c"), any())).thenReturn(Mono.just(failing));
        when(alertService.alert(eq("live.cleanup.failed"), eq("live-c"), any())).thenReturn(Mono.empty());
        when(workspace.recordingDir(anyString())).thenAnswer(invocation -> tempDir.resolve((String) invocation.getArgument(0)));

        var deleted = task.cleanupRetention().block();

        assertThat(deleted).isEqualTo(1);
        verify(metrics).cleanupFailed();
        verify(client, never()).delete(failing);
        verify(client).delete(succeeding);
        var mutation = ArgumentCaptor.forClass(Consumer.class);
        verify(statusService).mutate(eq("live-c"), mutation.capture());
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