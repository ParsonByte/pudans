package cn.tolim.pudans.core.live.recorder;

import cn.tolim.pudans.infra.properties.LiveProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 录制本地工作目录的唯一入口，分片与成品路径都从这里生成
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

    // 返回规范化后的录制工作根目录
    public Path workDir() {
        return Path.of(properties.getRecorder().getWorkDir()).toAbsolutePath().normalize();
    }

    // 校验录制名后拼接录制目录
    public Path recordingDir(String recordingName) {
        return workDir().resolve(recordingName(recordingName));
    }

    // 创建录制目录并返回，失败包装为状态异常
    public Path ensureRecordingDir(String recordingName) {
        var dir = recordingDir(recordingName);
        try {
            Files.createDirectories(dir);
        } catch (IOException error) {
            throw new IllegalStateException("创建录制目录失败：" + dir, error);
        }
        return dir;
    }

    // 分片清单固定存放在录制目录内
    public Path segmentsManifest(String recordingName) {
        return recordingDir(recordingName).resolve(SEGMENTS_MANIFEST);
    }

    // 成品清单固定存放在录制目录内
    public Path partsManifest(String recordingName) {
        return recordingDir(recordingName).resolve(PARTS_MANIFEST);
    }

    // 分片路径由录制目录与索引文件名生成
    public Path segmentPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(segmentFilename(index));
    }

    // 成品路径由录制目录与索引文件名生成
    public Path partPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(partFilename(index));
    }

    // 成品临时路径用于原子改名前的写入
    public Path partTempPath(String recordingName, int index) {
        return recordingDir(recordingName).resolve(partFilename(index) + TEMP_SUFFIX);
    }

    // 分片文件名按五位索引补零
    public static String segmentFilename(int index) {
        return String.format(SEGMENT_TEMPLATE, positiveIndex(index));
    }

    // 成品文件名按五位索引补零
    public static String partFilename(int index) {
        return String.format(PART_TEMPLATE, positiveIndex(index));
    }

    // 列出录制目录下已存在的分片文件
    public List<Path> listSegmentFiles(String recordingName) {
        return listFiles(recordingDir(recordingName), "segment-", ".ts");
    }

    // 列出录制目录下已存在的成品文件
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

    // 自底向上删除目录树，避免目录非空
    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // 按前缀与后缀筛选普通文件并排序
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

    // 只接受小写字母、数字与连字符，防止路径穿越
    private static String recordingName(String recordingName) {
        if (recordingName == null || !RECORDING_NAME.matcher(recordingName).matches()) {
            throw new IllegalArgumentException("录制名非法：" + recordingName);
        }
        return recordingName;
    }

    // 分片索引必须从一开始，避免生成非法文件名
    private static int positiveIndex(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("片索引必须为正数：" + index);
        }
        return index;
    }
}
