package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import cn.tolim.pudans.contract.core.extension.live.LiveRecording;
import cn.tolim.pudans.contract.core.extension.live.PartState;
import cn.tolim.pudans.contract.live.LiveErrorCode;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;

@ExtendWith(MockitoExtension.class)
class LiveDiskGuardTest {

    @Mock
    private LiveRecordingWorkspace workspace;

    @TempDir
    private Path tempDir;

    private LiveProperties properties;

    private LiveDiskGuard guard;

    @BeforeEach
    void setUp() {
        properties = new LiveProperties();
        guard = new LiveDiskGuard(workspace, properties);
        when(workspace.recordingDir(anyString())).thenReturn(tempDir);
        when(workspace.listSegmentFiles(anyString())).thenReturn(List.of());
    }

    @Test
    void stopsWhenFreeSpaceBelowThreshold() {
        properties.getRecorder().setMinFreeDiskBytes(DataSize.ofBytes(Long.MAX_VALUE));

        var pressure = guard.assess("live-a", recording());

        assertThat(pressure.blocked()).isTrue();
        assertThat(pressure.decision()).isEqualTo(LiveDiskGuard.DiskDecision.STOP_DISK_FULL);
        assertThat(pressure.errorCode()).isEqualTo(LiveErrorCode.LIVE_DISK_FULL);
        assertThatThrownBy(() -> guard.requireWritable("live-a", recording()))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_DISK_FULL.name()));
    }

    @Test
    void stopsWhenPendingPartsExceedLimit() {
        properties.getRecorder().setMinFreeDiskBytes(DataSize.ofBytes(1));
        var recording = recording();
        for (var index = 1; index <= 3; index++) {
            var part = new LiveRecording.Part();
            part.setIndex(index);
            part.setState(PartState.MERGING);
            recording.getStatus().getParts().add(part);
        }

        var pressure = guard.assess("live-b", recording);

        assertThat(pressure.decision()).isEqualTo(LiveDiskGuard.DiskDecision.STOP_STORAGE_BACKLOG);
        assertThat(pressure.errorCode()).isEqualTo(LiveErrorCode.LIVE_STORAGE_BACKLOG);
        assertThatThrownBy(() -> guard.requireWritable("live-b", recording))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(LiveErrorCode.LIVE_STORAGE_BACKLOG.name()));
    }

    @Test
    void mergesWhenLocalBytesExceedLimit() {
        properties.getRecorder().setMinFreeDiskBytes(DataSize.ofBytes(1));
        var recording = recording();
        recording.getStatus().setLocalBytes(DataSize.ofGigabytes(7).toBytes());

        var pressure = guard.assess("live-c", recording);

        assertThat(pressure.decision()).isEqualTo(LiveDiskGuard.DiskDecision.MERGE_NOW);
        assertThat(pressure.blocked()).isFalse();
    }

    @Test
    void allowsWritingWhenWithinWatermark() {
        properties.getRecorder().setMinFreeDiskBytes(DataSize.ofBytes(1));

        var pressure = guard.assess("live-d", recording());

        assertThat(pressure.decision()).isEqualTo(LiveDiskGuard.DiskDecision.OK);
        assertThat(pressure.errorCode()).isNull();
    }

    private static LiveRecording recording() {
        var recording = new LiveRecording();
        recording.getStatus().setParts(new ArrayList<>());
        return recording;
    }
}