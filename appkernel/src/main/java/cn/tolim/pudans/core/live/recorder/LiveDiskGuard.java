package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_DISK_FULL;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_BACKLOG;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.live.resource.LiveRecording;
import cn.tolim.pudans.contract.live.resource.PartState;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;

// 分片落盘前的磁盘水位判定，只负责判断与拒绝，不删除任何已有文件
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveDiskGuard {

    // 无历史分片时按 16MiB 预估单个分片占用
    private static final long DEFAULT_SEGMENT_BYTES = 16L * 1024 * 1024;

    private final LiveRecordingWorkspace workspace;

    private final LiveProperties properties;

    public enum DiskDecision {

        OK,

        MERGE_NOW,

        STOP_DISK_FULL,

        STOP_STORAGE_BACKLOG
    }

    public record DiskPressure(DiskDecision decision, LiveErrorCode errorCode, String message) {

        public boolean blocked() {
            return decision == DiskDecision.STOP_DISK_FULL || decision == DiskDecision.STOP_STORAGE_BACKLOG;
        }
    }

    public Path ensureRecordingDir(String recordingName) {
        workspace.ensureRecordingDir(recordingName);
        return workspace.recordingDir(recordingName);
    }

    public DiskPressure assess(String recordingName, LiveRecording recording) {
        var config = properties.getRecorder();
        var merge = config.getMerge();
        var dir = ensureRecordingDir(recordingName);
        var usable = usableBytes(dir);
        var minFree = config.getMinFreeDiskBytes().toBytes();
        var status = Optional.ofNullable(recording.getStatus()).orElseGet(LiveRecording.Status::new);
        var pendingParts = countPendingParts(status);
        var segments = workspace.listSegmentFiles(recordingName).size();
        if (usable < minFree) {
            return new DiskPressure(
                    DiskDecision.STOP_DISK_FULL,
                    LIVE_DISK_FULL,
                    "可用磁盘 " + usable + " 字节低于最小保留值 " + minFree + " 字节");
        }
        if (pendingParts > merge.getMaxPendingParts()) {
            return new DiskPressure(
                    DiskDecision.STOP_STORAGE_BACKLOG,
                    LIVE_STORAGE_BACKLOG,
                    "待上传成品 " + pendingParts + " 个超过上限 " + merge.getMaxPendingParts());
        }
        if (usable < minFree + estimatedSegmentBytes(status)) {
            return new DiskPressure(DiskDecision.MERGE_NOW, null, "可用磁盘接近最小保留值，需要先合并并上传");
        }
        if (status.getLocalBytes() > merge.getMaxLocalBytes().toBytes()) {
            return new DiskPressure(DiskDecision.MERGE_NOW, null, "本地缓存字节超过上限，需要先合并并上传");
        }
        if (segments > merge.getMaxLocalSegments()) {
            return new DiskPressure(
                    DiskDecision.MERGE_NOW, null, "未合并分片 " + segments + " 个超过上限 " + merge.getMaxLocalSegments());
        }
        return new DiskPressure(DiskDecision.OK, null, null);
    }

    // 空间或上传积压达到停止条件时抛出对应直播错误码，调用方应停止拉流并保留已有文件
    public DiskPressure requireWritable(String recordingName, LiveRecording recording) {
        var pressure = assess(recordingName, recording);
        if (pressure.blocked()) {
            throw pressure.errorCode().exception();
        }
        return pressure;
    }

    public long usableBytes(Path dir) {
        try {
            return Files.getFileStore(dir).getUsableSpace();
        } catch (IOException error) {
            throw new ApiException(LIVE_DISK_FULL.status(), LIVE_DISK_FULL.name(), "读取磁盘可用空间失败", error);
        }
    }

    // 未上传的成品和失败保留的成品仍然占用本地磁盘
    private static int countPendingParts(LiveRecording.Status status) {
        var parts = status.getParts();
        if (parts == null || parts.isEmpty()) {
            return 0;
        }
        return (int) parts.stream()
                .filter(part -> part.getState() != PartState.UPLOADED)
                .count();
    }

    private static long estimatedSegmentBytes(LiveRecording.Status status) {
        var parts = status.getParts();
        if (parts == null) {
            return DEFAULT_SEGMENT_BYTES;
        }
        var bytes = parts.stream()
                .filter(part -> part.getSize() > 0 && part.getSegmentCount() > 0)
                .mapToLong(LiveRecording.Part::getSize)
                .sum();
        var segments = parts.stream()
                .filter(part -> part.getSize() > 0 && part.getSegmentCount() > 0)
                .mapToInt(LiveRecording.Part::getSegmentCount)
                .sum();
        if (bytes <= 0 || segments <= 0) {
            return DEFAULT_SEGMENT_BYTES;
        }
        return Math.max(bytes / segments, 1);
    }
}
