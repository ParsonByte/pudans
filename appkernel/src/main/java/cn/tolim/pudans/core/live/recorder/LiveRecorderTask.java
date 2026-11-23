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
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // 断流与恢复告警复用统一去重入口，和失败告警共用同一套限制
    private final LiveAlerts liveAlerts;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final SourceStatus sourceStatus;

    private final String recordingName;

    private final String sourceName;

    private final String platform;

    private final String roomId;

    private final int segmentSeconds;

    private final int mergeIntervalSeconds;

    private final String storagePolicyName;

    private final String ownerInstance = UUID.randomUUID().toString();

    private final AtomicBoolean stopRequested = new AtomicBoolean();

    // 任务级断流计时，跨多次重连保留最初失败时刻
    private final LiveStreamOutage outage = new LiveStreamOutage();

    private volatile String currentUrl;

    private volatile Instant lastPacketAt;

    private volatile Instant lastHeartbeatAt;

    private volatile long reportedBytes;

    private volatile boolean heartbeatClosed;

    private volatile FFmpegFrameGrabber activeGrabber;

    private volatile Instant lastRemoteCheckAt;

    // 从录制资源提取任务所需的不可变参数
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
            LiveAlerts liveAlerts,
            ReactiveStringRedisTemplate redisTemplate,
            SourceStatus sourceStatus,
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
        this.liveAlerts = liveAlerts;
        this.redisTemplate = redisTemplate;
        this.sourceStatus = sourceStatus;
        this.recordingName = recording.getMetadata().getName();
        this.sourceName = recording.getSpec().getSourceName();
        this.platform = recording.getSpec().getPlatform().name();
        this.roomId = recording.getSpec().getRoomId();
        this.segmentSeconds = recording.getSpec().getSegmentSeconds();
        this.mergeIntervalSeconds = recording.getSpec().getMergeIntervalSeconds();
        this.storagePolicyName = recording.getSpec().getStoragePolicy();
    }

    // 对外暴露录制资源名，供日志与注册表使用
    public String recordingName() {
        return recordingName;
    }

    // 只设置停止标记，由拉流循环自行收尾
    public void requestStop() {
        stopRequested.set(true);
    }

    // 置停止标记并释放拉流器，用于停止超时兜底
    public void forceClose() {
        stopRequested.set(true);
        releaseActiveGrabber();
    }

    // 拉流器非空说明当前正在拉流
    public boolean isStreaming() {
        return activeGrabber != null;
    }

    // 执行一次完整录制：拉流、收尾并清理本实例资源
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

    // 校验排队状态后抢占录制所有权并开始心跳
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

    // 反复拉流直到正常收尾、失败或达到重试上限
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

    // 每轮拉流前检查停止、合并、磁盘与地址
    private Outcome prepare(Instant captureStartedAt) {
        if (stopRequested.get()) {
            return Outcome.stop();
        }
        if (merger.isFailed(recordingName)) {
            return Outcome.failure(merger.failureCode(recordingName), "录制成品处理失败");
        }
        try {
            var recording = recordingStatus.findRequired(recordingName).block();
            if (recording != null) {
                var pressure = diskGuard.requireWritable(recordingName, recording);
                if (pressure.decision() == LiveDiskGuard.DiskDecision.MERGE_NOW) {
                    merger.requestMerge(recordingName, false);
                }
            }
        } catch (ApiException error) {
            return Outcome.failure(codeOf(error, LIVE_DISK_FULL), error.getReason());
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
            if (opener.isAllowed(currentUrl)) {
                return null;
            }
            log.warn("当前拉流地址已不再允许，重新解析 [{}]", recordingName);
            currentUrl = null;
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

    // 循环读取媒体包写分片，直到停止或流中断
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
                // 重新收到数据包说明已恢复，先发恢复告警再刷新心跳
                notifyRecovered();
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

    // 按重试预算重解析地址并退避等待后重连
    private Outcome recover(Recovery recovery, LiveErrorCode lastCode, String message) {
        if (stopRequested.get()) {
            return Outcome.stop();
        }
        if (merger.isFailed(recordingName)) {
            return Outcome.failure(merger.failureCode(recordingName), "录制成品处理失败");
        }
        // 首次失败即开始计时，达到阈值才发一次断流告警
        var now = Instant.now();
        outage.begin(now);
        if (outage.alertDue(now, properties.getAlert().getStreamInterruptThreshold())) {
            var payload = alertPayload();
            payload.put("errorCode", lastCode.name());
            payload.put("message", message);
            sendAlert("live.stream.interrupted", payload);
            outage.markAlerted();
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

    // 断流后重新收到数据包时发送恢复告警并归零计时
    private void notifyRecovered() {
        var interrupted = outage.end(Instant.now());
        if (interrupted == null) {
            return;
        }
        var payload = alertPayload();
        payload.put("interruptedMillis", interrupted.toMillis());
        sendAlert("live.stream.recovered", payload);
    }

    // 组装告警公共字段，错误码按需追加
    private Map<String, Object> alertPayload() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("recordingName", recordingName);
        payload.put("sourceName", sourceName);
        payload.put("roomId", roomId);
        payload.put("at", Instant.now().toString());
        return payload;
    }

    // 告警异步发送，失败只记录日志
    private void sendAlert(String type, Map<String, Object> payload) {
        try {
            liveAlerts
                    .alert(type, recordingName, payload)
                    .onErrorResume(error -> {
                        log.warn("直播告警发送失败 [{}]：{}", type, error.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (RuntimeException error) {
            log.warn("直播告警发送失败 [{}]：{}", type, error.getMessage());
        }
    }

    // 解析房间信息，离线映射为正常结束
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

    // 按间隔写心跳，并上报字节与分片进度
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

    // 收尾合并上传，按结果完成或标记失败
    private void finish(Outcome outcome) {
        heartbeatClosed = true;
        try {
            merger.requestMerge(recordingName, true);
            merger.finalizeRecording(recordingName).block();
        } catch (RuntimeException error) {
            if (error.getCause() instanceof TimeoutException) {
                log.warn("录制收尾超过 finalize-timeout，标记失败 [{}]", recordingName);
                fail(LIVE_STREAM_FAILED, "录制收尾超时");
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

    // 全部成品上传后写最终统计并标记完成
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
            clearSourceRecording();
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
        clearSourceRecording();
        log.info("录制完成 [{}]，成品 {} 个，字节 {}", recordingName, parts.size(), totalBytes);
    }

    // 写失败状态并同步回直播源
    private void fail(LiveErrorCode code, String message) {
        try {
            recordingStatus.fail(recordingName, code, message).block();
        } catch (RuntimeException error) {
            log.warn("写入录制失败状态失败 [{}]：{}", recordingName, error.getMessage());
        }
        updateSourceFailed(code, message);
    }

    // 清除直播源的录制指针，空源名直接跳过
    private void clearSourceRecording() {
        if (!org.springframework.util.StringUtils.hasText(sourceName)) {
            return;
        }
        try {
            sourceStatus.recordingEnded(sourceName).block();
        } catch (RuntimeException error) {
            log.warn("清理直播源录制状态失败 [{}]：{}", sourceName, error.getMessage());
        }
    }

    // 把录制失败回写到关联直播源
    private void updateSourceFailed(LiveErrorCode code, String message) {
        if (!org.springframework.util.StringUtils.hasText(sourceName)) {
            return;
        }
        try {
            sourceStatus.recordingFailed(sourceName, code.name(), message).block();
        } catch (RuntimeException error) {
            log.warn("回写直播源录制失败状态失败 [{}]：{}", sourceName, error.getMessage());
        }
    }

    // 限频判断是否该读取跨实例停止信号
    private boolean remoteCheckDue() {
        var now = Instant.now();
        var last = lastRemoteCheckAt;
        if (last != null && Duration.between(last, now).compareTo(REMOTE_CHECK_INTERVAL) < 0) {
            return false;
        }
        lastRemoteCheckAt = now;
        return true;
    }

    // 读取停止键，Redis 异常时本轮忽略
    private boolean remoteStopRequested() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(stopKey()).blockOptional(REMOTE_CHECK_TIMEOUT).orElse(false));
        } catch (RuntimeException error) {
            log.debug("读取停止信号失败，本轮忽略 [{}]：{}", recordingName, error.getMessage());
            return false;
        }
    }

    // 定期续期 owner 键，供恢复流程判断存活
    private void renewOwner() {
        redisTemplate
                .opsForValue()
                .set(ownerKey(), ownerInstance, OWNER_TTL)
                .subscribe(ignored -> {}, error -> log.debug("写入录制 owner 失败 [{}]：{}", recordingName, error.getMessage()));
    }

    // 任务结束时删除 owner 与停止键
    private void cleanupRemoteKeys() {
        redisTemplate
                .delete(ownerKey(), stopKey())
                .subscribe(ignored -> {}, error -> log.debug("清理录制 Redis 键失败 [{}]：{}", recordingName, error.getMessage()));
    }

    // 停止并释放当前拉流器，重复调用无副作用
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

    // 归属键按录制名隔离不同实例
    private String ownerKey() {
        return LiveRecordingRecovery.OWNER_KEY_PREFIX + recordingName;
    }

    // 停止键按录制名跨实例共享
    private String stopKey() {
        return LiveRecordingRecovery.STOP_KEY_PREFIX + recordingName;
    }

    // 分段休眠以便停止请求及时生效
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

    // 优先复用异常携带的直播错误码，否则用兜底码
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

        // 构造正常收尾的结局
        static Outcome stop() {
            return new Outcome(null, null);
        }

        // 构造带错误码与说明的失败结局
        static Outcome failure(LiveErrorCode errorCode, String message) {
            return new Outcome(errorCode, message);
        }
    }

    private static final class Recovery {

        private int streamRetries;

        private int resolveAttempts;
    }
}
