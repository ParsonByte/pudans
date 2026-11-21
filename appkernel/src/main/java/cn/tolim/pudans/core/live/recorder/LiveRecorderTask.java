package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_DISK_FULL;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_NO_DATA;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RESOLVE_FAILED;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_ROOM_OFFLINE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STREAM_FAILED;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

// 单个录制任务：拉流写分片、断流重解析退避、收尾合并上传并落最终状态
@Slf4j
public class LiveRecorderTask implements Runnable {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private static final int MAX_RESOLVE_ATTEMPTS = 2;

    private static final long SLEEP_SLICE_MILLIS = 200;

    private static final Duration OWNER_TTL = Duration.ofSeconds(90);

    private static final Duration STOP_TTL = Duration.ofHours(1);

    private static final Duration REMOTE_CHECK_INTERVAL = Duration.ofSeconds(1);

    private static final Duration REMOTE_CHECK_TIMEOUT = Duration.ofMillis(200);

    private final LiveProperties properties;

    private final RecordingStatus recordingStatus;

    private final LiveRecorderRegistry registry;

    private final LivePartMerger merger;

    private final LiveRecordingWorkspace workspace;

    private final LiveSegmentManifest segmentManifest;

    private final LiveStreamOpener opener;

    private final RoomResolver roomResolver;

    private final LiveDiskGuard diskGuard;

    private final LiveRecorderBackoff backoff;

    private final LiveStoragePolicyResolver policyResolver;

    private final LiveMetrics metrics;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final String recordingName;

    private final String platform;

    private final String roomId;

    private final int segmentSeconds;

    private final int mergeIntervalSeconds;

    private final String storagePolicyName;

    private final String ownerInstance = UUID.randomUUID().toString();

    private final AtomicBoolean stopRequested = new AtomicBoolean();

    private volatile String currentUrl;

    private volatile Instant lastPacketAt;

    private volatile Instant lastHeartbeatAt;

    private volatile long reportedBytes;

    private volatile boolean heartbeatClosed;

    private volatile FFmpegFrameGrabber activeGrabber;

    private volatile Instant lastRemoteCheckAt;

    public LiveRecorderTask(
            LiveProperties properties,
            RecordingStatus recordingStatus,
            LiveRecorderRegistry registry,
            LivePartMerger merger,
            LiveRecordingWorkspace workspace,
            LiveSegmentManifest segmentManifest,
            LiveStreamOpener opener,
            RoomResolver roomResolver,
            LiveDiskGuard diskGuard,
            LiveRecorderBackoff backoff,
            LiveStoragePolicyResolver policyResolver,
            LiveMetrics metrics,
            ReactiveStringRedisTemplate redisTemplate,
            LiveRecording recording) {
        this.properties = properties;
        this.recordingStatus = recordingStatus;
        this.registry = registry;
        this.merger = merger;
        this.workspace = workspace;
        this.segmentManifest = segmentManifest;
        this.opener = opener;
        this.roomResolver = roomResolver;
        this.diskGuard = diskGuard;
        this.backoff = backoff;
        this.policyResolver = policyResolver;
        this.metrics = metrics;
        this.redisTemplate = redisTemplate;
        this.recordingName = recording.getMetadata().getName();
        this.platform = recording.getSpec().getPlatform().name();
        this.roomId = recording.getSpec().getRoomId();
        this.segmentSeconds = recording.getSpec().getSegmentSeconds();
        this.mergeIntervalSeconds = recording.getSpec().getMergeIntervalSeconds();
        this.storagePolicyName = recording.getSpec().getStoragePolicy();
    }

    public String recordingName() {
        return recordingName;
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public void forceClose() {
        stopRequested.set(true);
        releaseActiveGrabber();
    }

    public boolean isStreaming() {
        return activeGrabber != null;
    }

    @Override
    public void run() {
        try {
            if (!markRecording()) {
                return;
            }
            policyResolver.resolve(storagePolicyName).block();
            merger.start(recordingName, mergeIntervalSeconds);
            var outcome = capture();
            finish(outcome);
        } catch (ApiException error) {
            fail(codeOf(error, LIVE_STREAM_FAILED), error.getReason());
        } catch (RuntimeException error) {
            log.error("录制任务异常终止 [{}]", recordingName, error);
            fail(LIVE_STREAM_FAILED, error.getMessage());
        } finally {
            heartbeatClosed = true;
            releaseActiveGrabber();
            cleanupRemoteKeys();
            registry.unregister(recordingName);
            merger.stop(recordingName);
        }
    }

    private boolean markRecording() {
        var recording = recordingStatus.findRequired(recordingName).block();
        if (recording == null || recording.getStatus() == null) {
            return false;
        }
        if (recording.getStatus().getState() != LiveRecordingState.QUEUED) {
            log.info("录制任务已不在排队状态，跳过启动 [{}] state={}", recordingName, recording.getStatus().getState());
            return false;
        }
        if (stopRequested.get() || remoteStopRequested()) {
            log.info("录制任务已收到停止请求，跳过启动 [{}]", recordingName);
            return false;
        }
        recordingStatus.transition(recordingName, LiveRecordingState.RECORDING, null).block();
        recordingStatus
                .mutate(recordingName, status -> {
                    status.setOwnerInstance(ownerInstance);
                    status.setHeartbeatAt(Instant.now());
                })
                .block();
        renewOwner();
        return true;
    }

    private Outcome capture() {
        var recovery = new Recovery();
        var captureStartedAt = Instant.now();
        while (true) {
            var prepared = prepare(captureStartedAt);
            if (prepared != null) {
                return prepared;
            }
            FFmpegFrameGrabber grabber;
            try {
                grabber = opener.open(currentUrl);
            } catch (ApiException error) {
                log.warn("拉流失败 [{}]：{}", recordingName, error.getReason());
                var recovered = recover(recovery, LIVE_STREAM_FAILED, "拉流失败：" + error.getReason());
                if (recovered != null) {
                    return recovered;
                }
                continue;
            }
            activeGrabber = grabber;
            StreamFailure failure;
            try {
                failure = runStream(grabber);
            } finally {
                releaseActiveGrabber();
            }
            if (failure == null) {
                return Outcome.stop();
            }
            log.warn("直播流中断 [{}]：{}", recordingName, failure.message());
            var recovered = recover(recovery, failure.errorCode(), failure.message());
            if (recovered != null) {
                return recovered;
            }
        }
    }

    private Outcome prepare(Instant captureStartedAt) {
        if (stopRequested.get()) {
            return Outcome.stop();
        }
        if (merger.isFailed(recordingName)) {
            return Outcome.failure(merger.failureCode(recordingName), "录制成品处理失败");
        }
        if (Duration.between(captureStartedAt, Instant.now()).compareTo(properties.getRecorder().getMaxDuration()) >= 0) {
            log.info("录制达到最大时长，正常收尾 [{}]", recordingName);
            return Outcome.stop();
        }
        try {
            diskGuard.ensureRecordingDir(recordingName);
        } catch (ApiException error) {
            return Outcome.failure(codeOf(error, LIVE_DISK_FULL), error.getReason());
        }
        if (currentUrl != null) {
            return null;
        }
        var resolution = resolve();
        if (resolution.errorCode() != null) {
            return Outcome.failure(resolution.errorCode(), resolution.message());
        }
        if (resolution.info() == null || !resolution.info().live()) {
            log.info("直播已结束，正常收尾 [{}]", recordingName);
            return Outcome.stop();
        }
        try {
            currentUrl = opener.selectUrl(resolution.info(), null);
        } catch (ApiException error) {
            return Outcome.failure(codeOf(error, LIVE_STREAM_FAILED), error.getReason());
        }
        return null;
    }

    private StreamFailure runStream(FFmpegFrameGrabber grabber) {
        var writer = new LiveSegmentWriter(
                workspace, segmentManifest, properties, recordingName, grabber, segmentSeconds);
        var openedAt = Instant.now();
        var idleTimeout = properties.getRecorder().getStreamIdleTimeout();
        try {
            while (!stopRequested.get()) {
                if (merger.isFailed(recordingName)) {
                    return new StreamFailure(merger.failureCode(recordingName), "录制成品处理失败");
                }
                if (remoteCheckDue()) {
                    if (remoteStopRequested()) {
                        log.info("收到跨实例停止信号，结束当前拉流 [{}]", recordingName);
                        stopRequested.set(true);
                        break;
                    }
                }
                var now = Instant.now();
                var reference = lastPacketAt == null ? openedAt : lastPacketAt;
                if (Duration.between(reference, now).compareTo(idleTimeout) >= 0) {
                    return new StreamFailure(LIVE_STREAM_FAILED, "直播流空闲超时");
                }
                AVPacket packet;
                try {
                    packet = grabber.grabPacket();
                } catch (Exception error) {
                    return new StreamFailure(LIVE_STREAM_FAILED, "读取直播流失败：" + error.getMessage());
                }
                if (packet == null) {
                    return new StreamFailure(LIVE_STREAM_FAILED, "直播流已结束");
                }
                try {
                    var packetAt = Instant.now();
                    if (writer.shouldRotate(packet, packetAt)) {
                        writer.rotate(packetAt);
                        merger.requestMerge(recordingName, false);
                        if (merger.isFailed(recordingName)) {
                            return new StreamFailure(merger.failureCode(recordingName), "录制成品处理失败");
                        }
                    }
                    writer.record(packet, packetAt);
                } catch (ApiException error) {
                    metrics.segmentWriteFail();
                    return new StreamFailure(codeOf(error, LIVE_STREAM_FAILED), error.getReason());
                } finally {
                    av_packet_unref(packet);
                }
                lastPacketAt = writer.lastPacketAt();
                heartbeat(writer);
            }
            return null;
        } finally {
            writer.close();
            if (writer.segmentCount() > 0) {
                merger.requestMerge(recordingName, false);
            }
        }
    }

    private Outcome recover(Recovery recovery, LiveErrorCode lastCode, String message) {
        if (stopRequested.get()) {
            return Outcome.stop();
        }
        if (merger.isFailed(recordingName)) {
            return Outcome.failure(merger.failureCode(recordingName), "录制成品处理失败");
        }
        if (recovery.streamRetries >= properties.getRecorder().getMaxRetries()) {
            return Outcome.failure(lastCode, message);
        }
        recovery.streamRetries++;
        if (recovery.resolveAttempts < MAX_RESOLVE_ATTEMPTS) {
            recovery.resolveAttempts++;
            var resolution = resolve();
            if (resolution.errorCode() != null) {
                return Outcome.failure(resolution.errorCode(), resolution.message());
            }
            if (resolution.info() == null || !resolution.info().live()) {
                log.info("重解析确认直播已结束，正常收尾 [{}]", recordingName);
                return Outcome.stop();
            }
            var next = opener.selectUrl(resolution.info(), currentUrl);
            if (!next.equals(currentUrl)) {
                log.info("直播地址已变化，切换拉流地址 [{}]", recordingName);
                currentUrl = next;
                recovery.streamRetries = 0;
                recovery.resolveAttempts = 0;
            }
        }
        var delay = backoff.next(recovery.streamRetries);
        log.info(
                "断流第 {} 次重连 [{}]，等待 {} ms",
                recovery.streamRetries,
                recordingName,
                delay.toMillis());
        if (!sleep(delay)) {
            return Outcome.stop();
        }
        return null;
    }

    private Resolution resolve() {
        try {
            var info = roomResolver.resolve(LivePlatform.valueOf(platform), roomId, true).block();
            if (info == null) {
                return new Resolution(null, LIVE_RESOLVE_FAILED, "解析结果为空");
            }
            return new Resolution(info, null, null);
        } catch (ApiException error) {
            var code = codeOf(error, LIVE_RESOLVE_FAILED);
            if (code == LIVE_ROOM_OFFLINE) {
                return new Resolution(null, null, null);
            }
            return new Resolution(null, code, error.getReason());
        }
    }

    private void heartbeat(LiveSegmentWriter writer) {
        var now = Instant.now();
        if (heartbeatClosed) {
            return;
        }
        var last = lastHeartbeatAt;
        if (last != null && Duration.between(last, now).compareTo(HEARTBEAT_INTERVAL) < 0) {
            return;
        }
        lastHeartbeatAt = now;
        var bytes = writer.totalBytes() + writer.currentSize();
        renewOwner();
        recordingStatus
                .mutate(recordingName, status -> {
                    status.setHeartbeatAt(now);
                    if (writer.lastPacketAt() != null) {
                        status.setLastPacketAt(writer.lastPacketAt());
                    }
                    if (writer.lastWriteAt() != null) {
                        status.setLastWriteAt(writer.lastWriteAt());
                    }
                    status.setSegmentCount(writer.segmentCount());
                    status.setLocalBytes(merger.localBytes(recordingName));
                })
                .subscribe(updated -> {}, error -> log.warn("录制心跳写入失败 [{}]：{}", recordingName, error.getMessage()));
        if (bytes > reportedBytes) {
            metrics.recordingBytes(platform, bytes - reportedBytes);
            reportedBytes = bytes;
        }
    }

    private void finish(Outcome outcome) {
        heartbeatClosed = true;
        try {
            merger.requestMerge(recordingName, true);
            merger.finalizeRecording(recordingName).block();
        } catch (RuntimeException error) {
            if (error.getCause() instanceof TimeoutException) {
                log.warn("录制收尾超过 finalize-timeout，保持 FINALIZING [{}]", recordingName);
                return;
            }
            log.warn("录制收尾失败 [{}]：{}", recordingName, error.getMessage());
        }
        if (merger.isFailed(recordingName)) {
            fail(merger.failureCode(recordingName), "录制成品处理失败");
            return;
        }
        if (outcome.errorCode() != null) {
            fail(outcome.errorCode(), outcome.message());
            return;
        }
        complete();
    }

    private void complete() {
        var recording = recordingStatus.findRequired(recordingName).block();
        if (recording == null || recording.getStatus() == null) {
            return;
        }
        var status = recording.getStatus();
        var parts = status.getParts();
        if (parts == null || parts.isEmpty()) {
            log.info("录制未产生有效分片，标记取消 [{}]", recordingName);
            recordingStatus.transition(recordingName, LiveRecordingState.CANCELED, LIVE_NO_DATA.message()).block();
            return;
        }
        if (parts.stream().anyMatch(part -> part.getState() != PartState.UPLOADED)) {
            log.warn("录制仍有成品未上传，保持 FINALIZING [{}]", recordingName);
            return;
        }
        var first = parts.getFirst();
        var last = parts.getLast();
        var endedAt = last.getEndedAt() == null ? Instant.now() : last.getEndedAt();
        var durationMillis = first.getStartedAt() == null
                ? 0
                : Math.max(Duration.between(first.getStartedAt(), endedAt).toMillis(), 0);
        var totalBytes = parts.stream().mapToLong(LiveRecording.Part::getSize).sum();
        var segmentCount = parts.stream().mapToInt(LiveRecording.Part::getSegmentCount).sum();
        recordingStatus
                .mutate(recordingName, current -> {
                    current.setEndedAt(endedAt);
                    current.setDurationMillis(durationMillis);
                    current.setTotalBytes(totalBytes);
                    current.setSegmentCount(segmentCount);
                    current.setPartCount(parts.size());
                    current.setLocalBytes(merger.localBytes(recordingName));
                })
                .block();
        recordingStatus.transition(recordingName, LiveRecordingState.COMPLETED, null).block();
        log.info("录制完成 [{}]，成品 {} 个，字节 {}", recordingName, parts.size(), totalBytes);
    }

    private void fail(LiveErrorCode code, String message) {
        try {
            recordingStatus.fail(recordingName, code, message).block();
        } catch (RuntimeException error) {
            log.warn("写入录制失败状态失败 [{}]：{}", recordingName, error.getMessage());
        }
    }

    private boolean remoteCheckDue() {
        var now = Instant.now();
        var last = lastRemoteCheckAt;
        if (last != null && Duration.between(last, now).compareTo(REMOTE_CHECK_INTERVAL) < 0) {
            return false;
        }
        lastRemoteCheckAt = now;
        return true;
    }

    private boolean remoteStopRequested() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(stopKey()).blockOptional(REMOTE_CHECK_TIMEOUT).orElse(false));
        } catch (RuntimeException error) {
            log.debug("读取停止信号失败，本轮忽略 [{}]：{}", recordingName, error.getMessage());
            return false;
        }
    }

    private void renewOwner() {
        redisTemplate
                .opsForValue()
                .set(ownerKey(), ownerInstance, OWNER_TTL)
                .subscribe(ignored -> {}, error -> log.debug("写入录制 owner 失败 [{}]：{}", recordingName, error.getMessage()));
    }

    private void cleanupRemoteKeys() {
        redisTemplate
                .delete(ownerKey(), stopKey())
                .subscribe(ignored -> {}, error -> log.debug("清理录制 Redis 键失败 [{}]：{}", recordingName, error.getMessage()));
    }

    private void releaseActiveGrabber() {
        var grabber = activeGrabber;
        activeGrabber = null;
        if (grabber == null) {
            return;
        }
        try {
            grabber.stop();
        } catch (Exception error) {
            log.warn("关闭拉流器失败 [{}]：{}", recordingName, error.getMessage());
        }
        try {
            grabber.release();
        } catch (Exception error) {
            log.warn("释放拉流器失败 [{}]：{}", recordingName, error.getMessage());
        }
    }

    private String ownerKey() {
        return LiveRecordingRecovery.OWNER_KEY_PREFIX + recordingName;
    }

    private String stopKey() {
        return LiveRecordingRecovery.STOP_KEY_PREFIX + recordingName;
    }

    private boolean sleep(Duration duration) {
        var deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            if (stopRequested.get()) {
                return false;
            }
            var remaining = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
            try {
                Thread.sleep(Math.min(SLEEP_SLICE_MILLIS, remaining));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !stopRequested.get();
    }

    private static LiveErrorCode codeOf(Throwable error, LiveErrorCode fallback) {
        if (error instanceof ApiException apiException && apiException.getCode() != null) {
            try {
                return LiveErrorCode.valueOf(apiException.getCode());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private record StreamFailure(LiveErrorCode errorCode, String message) {}

    private record Resolution(LiveRoomInfo info, LiveErrorCode errorCode, String message) {}

    private record Outcome(LiveErrorCode errorCode, String message) {

        static Outcome stop() {
            return new Outcome(null, null);
        }

        static Outcome failure(LiveErrorCode errorCode, String message) {
            return new Outcome(errorCode, message);
        }
    }

    private static final class Recovery {

        private int streamRetries;

        private int resolveAttempts;
    }
}
