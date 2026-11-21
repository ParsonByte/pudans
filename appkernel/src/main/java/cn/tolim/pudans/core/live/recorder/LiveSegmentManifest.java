package cn.tolim.pudans.core.live.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 分片清单只追加写入并 fsync，崩溃后按清单与文件是否真实存在恢复
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSegmentManifest {

    private final ObjectMapper mapper;

    private final LiveRecordingWorkspace workspace;

    public void append(String recordingName, LiveSegment segment) {
        appendLine(workspace.segmentsManifest(recordingName), toJson(segment));
    }

    // 只返回文件仍然存在的分片，坏行忽略并告警
    public List<LiveSegment> closedSegments(String recordingName) {
        var manifest = workspace.segmentsManifest(recordingName);
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("读取分片清单失败：" + manifest, error);
        }
        var segments = new ArrayList<LiveSegment>();
        for (var line : lines) {
            if (line.isBlank()) {
                continue;
            }
            var segment = parse(recordingName, line);
            if (segment != null) {
                segments.add(segment);
            }
        }
        segments.sort(java.util.Comparator.comparingInt(LiveSegment::index));
        return segments;
    }

    // 下一个可用分片索引取清单与现存文件名中的最大值加一，重启后不覆盖已有分片
    public int nextIndex(String recordingName) {
        var manifest = workspace.segmentsManifest(recordingName);
        var max = 0;
        if (Files.isRegularFile(manifest)) {
            try {
                for (var line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        max = Math.max(max, mapper.readValue(line, LiveSegment.class).index());
                    } catch (IOException error) {
                        log.warn("分片清单存在坏行，忽略 [{}]：{}", recordingName, error.getMessage());
                    }
                }
            } catch (IOException error) {
                throw new IllegalStateException("读取分片清单失败：" + manifest, error);
            }
        }
        for (var path : workspace.listSegmentFiles(recordingName)) {
            max = Math.max(max, indexOf(path.getFileName().toString()));
        }
        return max + 1;
    }

    private static int indexOf(String filename) {
        var trimmed = filename.substring("segment-".length(), filename.length() - ".ts".length());
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private LiveSegment parse(String recordingName, String line) {
        try {
            var segment = mapper.readValue(line, LiveSegment.class);
            if (Files.isRegularFile(workspace.segmentPath(recordingName, segment.index()))) {
                return segment;
            }
            log.warn("分片清单记录的文件不存在，忽略 [{}] {}", recordingName, segment.filename());
            return null;
        } catch (IOException error) {
            log.warn("分片清单存在坏行，忽略 [{}]：{}", recordingName, error.getMessage());
            return null;
        }
    }

    private String toJson(LiveSegment segment) {
        try {
            return mapper.writeValueAsString(segment);
        } catch (IOException error) {
            throw new IllegalStateException("序列化分片清单失败：" + segment.filename(), error);
        }
    }

    static void appendLine(Path file, String line) {
        try (var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap((line + "\n").getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        } catch (IOException error) {
            throw new IllegalStateException("追加清单失败：" + file, error);
        }
    }
}
