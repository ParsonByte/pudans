package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.properties.LiveProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveRecordingWorkspaceTest {

    @TempDir
    Path tempDir;

    private LiveRecordingWorkspace workspace() {
        var properties = new LiveProperties();
        properties.getRecorder().setWorkDir(tempDir.toString());
        return new LiveRecordingWorkspace(properties);
    }

    @Test
    void namesSegmentsAndPartsWithFiveDigitIndex() {
        assertThat(LiveRecordingWorkspace.segmentFilename(1)).isEqualTo("segment-00001.ts");
        assertThat(LiveRecordingWorkspace.segmentFilename(42)).isEqualTo("segment-00042.ts");
        assertThat(LiveRecordingWorkspace.partFilename(1)).isEqualTo("part-00001.ts");
        assertThat(LiveRecordingWorkspace.partFilename(12345)).isEqualTo("part-12345.ts");
    }

    @Test
    void buildsPathsUnderRecordingDirectory() {
        var workspace = workspace();
        var dir = workspace.recordingDir("rec-douyin-abc-20261120120000-abcd");

        assertThat(dir.getParent()).isEqualTo(workspace.workDir());
        assertThat(workspace.segmentPath("rec-douyin-abc-20261120120000-abcd", 3))
                .isEqualTo(dir.resolve("segment-00003.ts"));
        assertThat(workspace.partPath("rec-douyin-abc-20261120120000-abcd", 2))
                .isEqualTo(dir.resolve("part-00002.ts"));
        assertThat(workspace.partTempPath("rec-douyin-abc-20261120120000-abcd", 2))
                .isEqualTo(dir.resolve("part-00002.ts.tmp"));
        assertThat(workspace.segmentsManifest("rec-douyin-abc-20261120120000-abcd"))
                .isEqualTo(dir.resolve("segments.jsonl"));
    }

    @Test
    void rejectsIllegalRecordingNamesAndIndexes() {
        var workspace = workspace();

        assertThatThrownBy(() -> workspace.recordingDir("../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.recordingDir("Rec-Upper")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.recordingDir("rec/a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.recordingDir("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.recordingDir(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LiveRecordingWorkspace.segmentFilename(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsOnlySegmentAndPartFiles() throws IOException {
        var workspace = workspace();
        var dir = workspace.ensureRecordingDir("rec-a");
        Files.writeString(dir.resolve("segment-00002.ts"), "");
        Files.writeString(dir.resolve("segment-00001.ts"), "");
        Files.writeString(dir.resolve("part-00001.ts"), "");
        Files.writeString(dir.resolve("segments.jsonl"), "");

        assertThat(workspace.listSegmentFiles("rec-a"))
                .extracting(path -> path.getFileName().toString())
                .containsExactly("segment-00001.ts", "segment-00002.ts");
        assertThat(workspace.listPartFiles("rec-a"))
                .extracting(path -> path.getFileName().toString())
                .containsExactly("part-00001.ts");
        assertThat(workspace.listSegmentFiles("rec-missing")).isEmpty();
    }

    @Test
    void deletesRecordingDirectoryRecursively() throws IOException {
        var workspace = workspace();
        var dir = workspace.ensureRecordingDir("rec-b");
        Files.writeString(dir.resolve("segment-00001.ts"), "data");
        Files.createDirectories(dir.resolve("nested"));
        Files.writeString(dir.resolve("nested").resolve("parts.jsonl"), "{}");

        workspace.deleteRecordingDir("rec-b");

        assertThat(Files.exists(dir)).isFalse();
        assertThat(Files.exists(workspace.workDir())).isTrue();
    }
}
