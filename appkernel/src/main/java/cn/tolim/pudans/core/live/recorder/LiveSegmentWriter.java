package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STREAM_FAILED;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;

import cn.tolim.pudans.infra.properties.LiveProperties;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;

// 分片写入器：流复制写 mpegts，按关键帧与时长切段并追加本地清单
@Slf4j
public class LiveSegmentWriter implements AutoCloseable {

    private static final String FORMAT = "mpegts";

    private final LiveRecordingWorkspace workspace;

    private final LiveSegmentManifest manifest;

    private final String recordingName;

    private final FFmpegFrameGrabber grabber;

    private final Duration segmentDuration;

    private final int videoStreamIndex;

    private final int audioStreamIndex;

    private FFmpegFrameRecorder recorder;

    private int index = 1;

    private Instant segmentStartedAt;

    private Instant lastPacketAt;

    private Instant lastWriteAt;

    private boolean writtenInSegment;

    private int closedSegments;

    private long totalSize;

    private long totalDurationMillis;

    // 从抓取器提取流索引并从清单续接分片编号
    public LiveSegmentWriter(
            LiveRecordingWorkspace workspace,
            LiveSegmentManifest manifest,
            LiveProperties properties,
            String recordingName,
            FFmpegFrameGrabber grabber,
            int segmentSeconds) {
        this.workspace = workspace;
        this.manifest = manifest;
        this.recordingName = recordingName;
        this.grabber = grabber;
        this.segmentDuration = Duration.ofSeconds(segmentSeconds);
        this.index = manifest.nextIndex(recordingName);
        this.videoStreamIndex = grabber.getVideoStream();
        this.audioStreamIndex = grabber.getAudioStream();
    }

    // 切段必须是纯判断，方便单测覆盖关键帧与时长条件
    public boolean shouldRotate(AVPacket packet, Instant now) {
        if (recorder == null || !writtenInSegment || segmentStartedAt == null) {
            return false;
        }
        if (Duration.between(segmentStartedAt, now).compareTo(segmentDuration) < 0) {
            return false;
        }
        if (videoStreamIndex < 0) {
            return true;
        }
        return packet.stream_index() == videoStreamIndex && (packet.flags() & AV_PKT_FLAG_KEY) != 0;
    }

    // 只写音视频包，首个关键帧到达时才打开写入器
    public void record(AVPacket packet, Instant now) {
        lastPacketAt = now;
        if (packet.stream_index() != videoStreamIndex && packet.stream_index() != audioStreamIndex) {
            return;
        }
        if (recorder == null && !openIfReady(packet, now)) {
            return;
        }
        try {
            recorder.recordPacket(packet);
        } catch (Exception error) {
            throw LIVE_STREAM_FAILED.exception();
        }
        writtenInSegment = true;
        lastWriteAt = now;
    }

    // 切段时关闭当前分片并返回其元数据
    public LiveSegment rotate(Instant now) {
        return closeCurrent(now);
    }

    // 关闭当前分片，供调用方指定结束时间
    public LiveSegment close(Instant now) {
        return closeCurrent(now);
    }

    // 关闭写入器时以当前时间收尾当前分片
    @Override
    public void close() {
        closeCurrent(Instant.now());
    }

    // 已关闭分片数加上正在写入的分片
    public int segmentCount() {
        return closedSegments + (recorder == null ? 0 : 1);
    }

    // 已关闭分片的累计字节数
    public long totalBytes() {
        return totalSize;
    }

    // 当前分片文件大小，未打开时为零
    public long currentSize() {
        if (recorder == null) {
            return 0;
        }
        try {
            return Files.size(workspace.segmentPath(recordingName, index));
        } catch (IOException error) {
            return 0;
        }
    }

    // 最近收到媒体包的时间，用于空闲超时判断
    public Instant lastPacketAt() {
        return lastPacketAt;
    }

    // 最近写出媒体包的时间，随心跳上报
    public Instant lastWriteAt() {
        return lastWriteAt;
    }

    // 等到关键帧或音频包才创建分片文件
    private boolean openIfReady(AVPacket packet, Instant now) {
        if (videoStreamIndex >= 0) {
            if (packet.stream_index() != videoStreamIndex || (packet.flags() & AV_PKT_FLAG_KEY) == 0) {
                return false;
            }
        } else if (audioStreamIndex < 0 || packet.stream_index() != audioStreamIndex) {
            return false;
        }
        open(now);
        return true;
    }

    // 以流复制参数创建 mpegts 分片写入器
    private void open(Instant now) {
        var path = workspace.segmentPath(recordingName, index);
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            throw new IllegalStateException("清理旧分片失败：" + path, error);
        }
        var newRecorder = new FFmpegFrameRecorder(path.toFile(), 0);
        newRecorder.setFormat(FORMAT);
        if (grabber.getAudioChannels() > 0 && grabber.getAudioBitrate() > 0 && grabber.getSampleRate() > 0) {
            newRecorder.setAudioChannels(grabber.getAudioChannels());
            newRecorder.setSampleRate(grabber.getSampleRate());
            newRecorder.setAudioBitrate(128000);
        }
        try {
            newRecorder.start(grabber.getFormatContext());
        } catch (FFmpegFrameRecorder.Exception error) {
            releaseQuietly(newRecorder);
            throw LIVE_STREAM_FAILED.exception();
        }
        recorder = newRecorder;
        segmentStartedAt = now;
        writtenInSegment = false;
    }

    // 收尾当前分片，无有效数据时删除空文件
    private LiveSegment closeCurrent(Instant now) {
        if (recorder == null) {
            return null;
        }
        var path = workspace.segmentPath(recordingName, index);
        var startedAt = segmentStartedAt;
        var hadPackets = writtenInSegment;
        stopQuietly(recorder);
        recorder = null;
        var size = StorageIo.sizeOf(path);
        if (!hadPackets || size <= 0) {
            StorageIo.deleteQuietly(path);
            index++;
            return null;
        }
        var endedAt = now;
        var durationMillis = Math.max(Duration.between(startedAt, endedAt).toMillis(), 0);
        var segment = new LiveSegment(index, workspace.segmentFilename(index), startedAt, endedAt, durationMillis, size);
        manifest.append(recordingName, segment);
        totalSize += size;
        totalDurationMillis += durationMillis;
        closedSegments++;
        index++;
        return segment;
    }

    // 停止失败只告警，仍需释放写入器
    private static void stopQuietly(FFmpegFrameRecorder recorder) {
        try {
            recorder.stop();
        } catch (Exception error) {
            log.warn("关闭分片写入器失败：{}", error.getMessage());
        } finally {
            releaseQuietly(recorder);
        }
    }

    // 释放失败只告警，避免影响主流程
    private static void releaseQuietly(FFmpegFrameRecorder recorder) {
        try {
            recorder.release();
        } catch (Exception error) {
            log.warn("释放分片写入器失败：{}", error.getMessage());
        }
    }
}
