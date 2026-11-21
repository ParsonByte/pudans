package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_INVALID_ARGUMENT;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_ACTIVE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_READY;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_SOURCE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.resource.LiveRecording;
import cn.tolim.pudans.contract.live.resource.LiveRecordingState;
import cn.tolim.pudans.contract.live.resource.LiveSource;
import cn.tolim.pudans.contract.live.resource.PartState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;
import cn.tolim.pudans.core.live.recorder.LiveRecorder;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RecordingsTest {

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private RecordingStatus statusService;

    @Mock
    private LiveStoragePolicyResolver policyResolver;

    @Mock
    private LiveRecorder recorderService;

    @Mock
    private LiveRecorderRegistry registry;

    @Mock
    private RoomResolver resolveService;

    @InjectMocks
    private Recordings service;

    @Test
    void retryRejectsActiveRecording() {
        var recording = recording(LiveRecordingState.RECORDING);
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", null, "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_RECORDING_ACTIVE))
                .verify();
    }

    @Test
    void retryRejectsTerminalRecordingWithoutFailure() {
        var recording = recording(LiveRecordingState.COMPLETED);
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", null, "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_RECORDING_NOT_READY))
                .verify();
    }

    @Test
    void retryRejectsUnknownMode() {
        var recording = recording(LiveRecordingState.FAILED);
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(recorderService.hasLocalData(recording)).thenReturn(true);

        StepVerifier.create(service.retry("rec-1", "SOMETHING", "tester"))
                .expectErrorSatisfies(error -> assertCode(error, LIVE_INVALID_ARGUMENT))
                .verify();
    }

    @Test
    void retryRejectsUploadWithoutLocalData() {
        var recording = recording(LiveRecordingState.FAILED);
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(recorderService.hasLocalData(recording)).thenReturn(false);

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
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(recorderService.hasLocalData(recording)).thenReturn(true);
        when(recorderService.resumeUpload(recording)).thenReturn(Mono.just(recording));
        when(statusService.mutate(eq("rec-1"), any())).thenReturn(Mono.just(recording));
        when(statusService.transition("rec-1", LiveRecordingState.COMPLETED, "续传完成"))
                .thenReturn(Mono.just(recording));

        StepVerifier.create(service.retry("rec-1", "upload", "tester"))
                .assertNext(result -> {
                    assertThat(result.recording()).isEqualTo("rec-1");
                    assertThat(result.mode()).isEqualTo("UPLOAD");
                })
                .verifyComplete();
    }

    @Test
    void retryStreamRequiresExistingSource() {
        var recording = recording(LiveRecordingState.FAILED);
        when(statusService.findRequired("rec-1")).thenReturn(Mono.just(recording));
        when(recorderService.hasLocalData(recording)).thenReturn(false);
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
}
