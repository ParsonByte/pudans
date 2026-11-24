package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_INVALID_ARGUMENT;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_ACTIVE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_READY;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_SOURCE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;
import cn.tolim.pudans.core.live.recorder.LiveRecorder;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RecordingsTest {

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private RecordingStatus recordingStatus;

    @Mock
    private LiveStoragePolicyResolver policyResolver;

    @Mock
    private LiveRecorder liveRecorder;

    @Mock
    private LiveRecorderRegistry registry;

    @Mock
    private RoomResolver roomResolver;

    @Mock
    private LiveRecordingLocks recordingLocks;

    @InjectMocks
    private Recordings service;

    @Test
    void retryRejectsActiveRecording() {
        var recording = recording(LiveRecordingState.RECORDING);
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", null, "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_RECORDING_ACTIVE))
                .verify();
    }

    @Test
    void retryRejectsTerminalRecordingWithoutFailure() {
        var recording = recording(LiveRecordingState.COMPLETED);
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", null, "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_RECORDING_NOT_READY))
                .verify();
    }

    @Test
    void retryRejectsUnknownMode() {
        var recording = recording(LiveRecordingState.FAILED);
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(true);

        StepVerifier.create(service.retry("rec-1", "SOMETHING", "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_INVALID_ARGUMENT))
                .verify();
    }

    @Test
    void retryRejectsUploadWithoutLocalData() {
        var recording = recording(LiveRecordingState.FAILED);
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(false);

        StepVerifier.create(service.retry("rec-1", "upload", "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_RECORDING_NOT_READY))
                .verify();
    }

    @Test
    void retryUploadCompletesRecording() {
        var recording = recording(LiveRecordingState.FAILED);
        var part = new LiveRecording.Part();
        part.setIndex(0);
        part.setState(PartState.UPLOADED);
        part.setStartedAt(Instant.parse("2026-11-20T10:00:00Z"));
        part.setEndedAt(Instant.parse("2026-11-20T10:10:00Z"));
        part.setSize(1024L);
        part.setSegmentCount(60);
        recording.getStatus().setParts(new ArrayList<>(List.of(part)));
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(true);
        when(liveRecorder.resumeUpload(recording)).thenReturn(Mono.just(recording));
        when(recordingStatus.completeAfterResume(eq("rec-1"), any())).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", "upload", "tester"))
                .assertNext(result -> {
                    assertThat(result.recording()).isEqualTo("rec-1");
                    assertThat(result.mode()).isEqualTo("UPLOAD");
                })
                .verifyComplete();
    }

    @Test
    void summaryAggregatesStatesAndBytes() {
        var queued = recording(LiveRecordingState.QUEUED);
        queued.getStatus().setTotalBytes(0L);
        queued.getStatus().setLocalBytes(0L);
        var active = recording(LiveRecordingState.RECORDING);
        active.getStatus().setTotalBytes(1024L);
        active.getStatus().setLocalBytes(512L);
        var finalizing = recording(LiveRecordingState.FINALIZING);
        finalizing.getStatus().setTotalBytes(512L);
        finalizing.getStatus().setLocalBytes(0L);
        var completed = recording(LiveRecordingState.COMPLETED);
        completed.getStatus().setTotalBytes(2048L);
        completed.getStatus().setLocalBytes(0L);
        var failed = recording(LiveRecordingState.FAILED);
        failed.getStatus().setTotalBytes(4096L);
        failed.getStatus().setLocalBytes(1024L);
        var canceled = recording(LiveRecordingState.CANCELED);
        canceled.getStatus().setTotalBytes(256L);
        canceled.getStatus().setLocalBytes(0L);
        when(client.listAll(eq(LiveRecording.class), any(), any()))
                .thenReturn(Flux.just(queued, active, finalizing, completed, failed, canceled));

        StepVerifier.create(service.summary())
                .assertNext(summary -> {
                    assertThat(summary.total()).isEqualTo(6L);
                    assertThat(summary.active()).isEqualTo(3L);
                    assertThat(summary.completed()).isEqualTo(1L);
                    assertThat(summary.failed()).isEqualTo(1L);
                    assertThat(summary.canceled()).isEqualTo(1L);
                    assertThat(summary.totalBytes()).isEqualTo(7936L);
                    assertThat(summary.localBytes()).isEqualTo(1536L);
                })
                .verifyComplete();
    }

    @Test
    void retryStreamRequiresExistingSource() {
        var recording = recording(LiveRecordingState.FAILED);
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(false);
        when(client.fetch(LiveSource.class, "douyin-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.retry("rec-1", "stream", "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_SOURCE_NOT_FOUND))
                .verify();
    }

    private static void assertCode(Throwable error, cn.tolim.pudans.contract.live.dto.LiveErrorCode expected) {
        assertThat(error).isInstanceOfSatisfying(ApiException.class,
                api -> assertThat(api.getCode()).isEqualTo(expected.name()));
    }

    private static LiveRecording recording(LiveRecordingState state) {
        var recording = new LiveRecording();
        recording.setMetadata(new Metadata());
        recording.getMetadata().setName("rec-1");
        var spec = new LiveRecording.Spec();
        spec.setSourceName("douyin-1");
        recording.setSpec(spec);
        var status = new LiveRecording.Status();
        status.setState(state);
        recording.setStatus(status);
        return recording;
    }

    @Test
    void retryUploadPassesAggregatedStatsToCompletion() {
        var recording = recording(LiveRecordingState.FAILED);
        var part = new LiveRecording.Part();
        part.setIndex(0);
        part.setState(PartState.UPLOADED);
        part.setStartedAt(Instant.parse("2026-11-20T10:00:00Z"));
        part.setEndedAt(Instant.parse("2026-11-20T10:10:00Z"));
        part.setSize(1024L);
        part.setSegmentCount(60);
        recording.getStatus().setParts(new ArrayList<>(List.of(part)));
        var captured = new AtomicReference<Consumer<LiveRecording.Status>>();
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(true);
        when(liveRecorder.resumeUpload(recording)).thenReturn(Mono.just(recording));
        when(recordingStatus.completeAfterResume(eq("rec-1"), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            return Mono.just(recording);
        });

        StepVerifier.create(service.retry("rec-1", "upload", "tester")).expectNextCount(1).verifyComplete();

        var status = new LiveRecording.Status();
        captured.get().accept(status);
        assertThat(status.getEndedAt()).isEqualTo(Instant.parse("2026-11-20T10:10:00Z"));
        assertThat(status.getDurationMillis()).isEqualTo(600_000L);
        assertThat(status.getTotalBytes()).isEqualTo(1024L);
        assertThat(status.getSegmentCount()).isEqualTo(60);
        assertThat(status.getPartCount()).isEqualTo(1);
        assertThat(status.getLocalBytes()).isZero();
    }

    @Test
    void retryUploadKeepsFailedWhenAnyPartIsNotUploaded() {
        var recording = recording(LiveRecordingState.FAILED);
        var uploaded = new LiveRecording.Part();
        uploaded.setIndex(0);
        uploaded.setState(PartState.UPLOADED);
        var pending = new LiveRecording.Part();
        pending.setIndex(1);
        pending.setState(PartState.FAILED);
        recording.getStatus().setParts(new ArrayList<>(List.of(uploaded, pending)));
        when(recordingStatus.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(liveRecorder.hasLocalData(recording)).thenReturn(true);
        when(liveRecorder.resumeUpload(recording)).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", "upload", "tester"))
                .assertNext(result -> assertThat(result.mode()).isEqualTo("UPLOAD"))
                .verifyComplete();

        verify(recordingStatus, never()).completeAfterResume(any(), any());
    }
}
