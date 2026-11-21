package cn.tolim.pudans.core.live.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RecordingStatusTest {

    private RecordingStatus service;

    private LiveRecording recording;

    @BeforeEach
    void setUp() {
        var client = mock(ReactiveExtensionClient.class);
        service = new RecordingStatus(client);
        recording = recording("rec-a", LiveRecordingState.QUEUED);
        when(client.fetch(LiveRecording.class, "rec-a")).thenReturn(Mono.just(recording));
        when(client.update(any(LiveRecording.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void transitionToRecordingFillsStartedAtAndMessage() {
        var updated = service.transition("rec-a", LiveRecordingState.RECORDING, "开始拉流").block();

        assertThat(updated).isNotNull();
        assertThat(updated.getStatus().getState()).isEqualTo(LiveRecordingState.RECORDING);
        assertThat(updated.getStatus().getMessage()).isEqualTo("开始拉流");
        assertThat(updated.getStatus().getStartedAt()).isNotNull();
    }

    @Test
    void transitionOutOfTerminalStateIsRejected() {
        recording.getStatus().setState(LiveRecordingState.COMPLETED);

        assertThatThrownBy(() -> service.transition("rec-a", LiveRecordingState.RECORDING, "重开").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(LiveErrorCode.LIVE_RECORDING_NOT_ACTIVE.name()));
    }

    @Test
    void failKeepsMostBlockingErrorCodeAndIgnoresCompletedRecording() {
        recording.getStatus().setState(LiveRecordingState.FAILED);
        recording.getStatus().setErrorCode(LiveErrorCode.LIVE_STREAM_FAILED.name());

        var diskFull = service.fail("rec-a", LiveErrorCode.LIVE_DISK_FULL, "磁盘不足").block();
        assertThat(diskFull.getStatus().getErrorCode()).isEqualTo(LiveErrorCode.LIVE_DISK_FULL.name());
        assertThat(diskFull.getStatus().getMessage()).isEqualTo("磁盘不足");
        assertThat(diskFull.getStatus().getEndedAt()).isNotNull();

        var rateLimited = service.fail("rec-a", LiveErrorCode.LIVE_RATE_LIMITED, "限流").block();
        assertThat(rateLimited.getStatus().getErrorCode()).isEqualTo(LiveErrorCode.LIVE_DISK_FULL.name());
        assertThat(rateLimited.getStatus().getMessage()).isEqualTo("磁盘不足");

        recording.getStatus().setState(LiveRecordingState.COMPLETED);
        recording.getStatus().setErrorCode(null);
        var completed = service.fail("rec-a", LiveErrorCode.LIVE_STORAGE_FAILED, "上传失败").block();
        assertThat(completed.getStatus().getState()).isEqualTo(LiveRecordingState.COMPLETED);
        assertThat(completed.getStatus().getErrorCode()).isNull();
    }

    @Test
    void addPartReplacesSameIndexAndSortsByIndex() {
        var first = part(2);
        var second = part(1);

        service.addPart("rec-a", first).block();
        var updated = service.addPart("rec-a", second).block();

        assertThat(updated.getStatus().getParts()).extracting(LiveRecording.Part::getIndex).containsExactly(1, 2);
        assertThat(updated.getStatus().getPartCount()).isEqualTo(2);
    }

    @Test
    void mutatePartUpdatesOnlyTargetPart() {
        service.addPart("rec-a", part(1)).block();
        service.addPart("rec-a", part(2)).block();

        var updated = service.mutatePart("rec-a", 2, stored -> stored.setSize(1024L)).block();

        assertThat(updated).isNotNull();
        var target = updated.getStatus().getParts().stream()
                .filter(stored -> stored.getIndex() == 2)
                .findFirst()
                .orElseThrow();
        var untouched = updated.getStatus().getParts().stream()
                .filter(stored -> stored.getIndex() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(target.getSize()).isEqualTo(1024L);
        assertThat(untouched.getSize()).isZero();
    }

    private static LiveRecording recording(String name, LiveRecordingState state) {
        var recording = new LiveRecording();
        recording.setMetadata(new Metadata());
        recording.getMetadata().setName(name);
        recording.setSpec(new LiveRecording.Spec());
        recording.setStatus(new LiveRecording.Status());
        recording.getStatus().setState(state);
        return recording;
    }

    private static LiveRecording.Part part(int index) {
        var part = new LiveRecording.Part();
        part.setIndex(index);
        part.setFilename(cn.tolim.pudans.core.live.recorder.LiveRecordingWorkspace.partFilename(index));
        return part;
    }
}
