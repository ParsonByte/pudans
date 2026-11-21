package cn.tolim.pudans.core.live.recorder;

import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 录制本地工作目录的唯一入口，分片与成品路径都从这里生成
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecordingWorkspace {

    private static final Pattern RECORDING_NAME = Pattern.compile("[a-z0-9-]{1,120}");

    private static final String SEGMENTS_MANIFEST = "segments.jsonl";

    private static final String PARTS_MANIFEST = "parts.jsonl";

    private static final String SEGMENT_TEMPLATE = "segment-%05d.ts";

    private static final String PART_TEMPLATE = "part-%05d.ts";

    private static final String TEMP_SUFFIX = ".tmp";

    private final LiveProperties properties;

    public Path workDir() {
        return Path.of(properties.getRecorder().getWorkDir()).toAbsolutePath().normalize();
    }

    public Path recordingDir(String recordingName) {
        return workDir().resolve(recordingName(recordingName));
    }

    public Path ensureRecordingDir(String recordingName) {
        var dir = recordingDir(recordingName);
        try {
            Files.createDirectories(dir);
        } catch (IOException error) {
            throw new IllegalStateException("创建录制目录失败：" + dir, error);
        }
        return dir;
    }

    public Path segmentsManifest(String recordingName) {
        return recordingDir(recordingName).resolve(SEGMENTS_MANIFEST);
    }

    public Path partsManifest(String recordingName) {
        return recordingDir(recordingName).resolve(PARTS_MANIFEST);
    }

    public Path segmentPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(segmentFilename(index));
    }

    public Path partPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(partFilename(index));
    }

    public Path partTempPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(partFilename(index) + TEMP_SUFFIX);
    }

    public static String segmentFilename(int index) {
        return String.format(SEGMENT_TEMPLATE, positiveIndex(index));
    }

    public static String partFilename(int index) {
        return String.format(PART_TEMPLATE, positiveIndex(index));
    }

    public List<Path> listSegmentFiles(String recordingName) {
        return listFiles(recordingDir(recordingName), "segment-", ".ts");
    }

    public List<Path> listPartFiles(String recordingName) {
        return listFiles(recordingDir(recordingName), "part-", ".ts");
    }

    // 删除前用 realPath 校验目录必须位于工作目录下，删除过程不跟随符号链接
    public void deleteRecordingDir(String recordingName) {
        var dir = recordingDir(recordingName);
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            var root = workDir().toRealPath();
            var target = dir.toRealPath();
            if (!target.startsWith(root) || target.equals(root)) {
                throw new IllegalArgumentException("录制目录越界：" + dir);
            }
            deleteRecursively(dir);
        } catch (IOException error) {
            throw new IllegalStateException("删除录制目录失败：" + dir, error);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static List<Path> listFiles(Path dir, String prefix, String suffix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        var name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("列出录制目录失败：" + dir, error);
        }
    }

    private static String recordingName(String recordingName) {
        if (recordingName == null || !RECORDING_NAME.matcher(recordingName).matches()) {
            throw new IllegalArgumentException("录制名非法：" + recordingName);
        }
        return recordingName;
    }

    private static int positiveIndex(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("片索引必须为正数：" + index);
        }
        return index;
    }
}
