package cn.tolim.pudans.core.live.reconciler;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.extension.api.ExtensionClient;
import cn.tolim.pudans.extension.api.ExtensionUtil;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.storage.StorageTransfer;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.live.recorder.LiveRecordingWorkspace;
import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;

// 删除录制时兜底清理对象与本地目录，全部成功后才移除 finalizer
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecordingReconciler implements Reconciler<Reconciler.Request> {

    public static final String FINALIZER = "pudans.tolim.cn/live-recording-cleanup";

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private static final Duration STOP_REQUEUE = Duration.ofSeconds(5);

    private static final Duration FAILURE_RETRY = Duration.ofSeconds(30);

    private static final Duration SLOW_RETRY = Duration.ofHours(1);

    private static final int MAX_ATTEMPTS = 10;

    private final ExtensionClient client;

    private final StorageTransfer storageTransfer;

    private final RecordingStatus recordingStatus;

    private final LiveRecordingWorkspace workspace;

    private final LiveRecorderRegistry registry;

    private final LiveMetrics metrics;

    private final LiveAlerts liveAlerts;

    @Override
    public Result reconcile(Reconciler.Request request) {
        var optional = client.fetch(LiveRecording.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var recording = optional.get();
        if (!ExtensionUtil.isDeleted(recording)) {
            if (ExtensionUtil.addFinalizers(recording.getMetadata(), Set.of(FINALIZER))) {
                client.update(recording);
            }
            return Result.doNotRetry();
        }
        var name = recording.getMetadata().getName();
        var state = recording.getStatus() == null ? null : recording.getStatus().getState();
        if (isActive(state)) {
            if (registry.isActive(name)) {
                return stopAndRequeue(name);
            }
            if (!isOwnerLost(recording)) {
                return Result.requeue(STOP_REQUEUE);
            }
            markRecorderLost(name);
        }
        return cleanup(recording);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new LiveRecording()).build();
    }

    private Result stopAndRequeue(String name) {
        try {
            registry.requestStop(name).block(STOP_TIMEOUT);
        } catch (RuntimeException error) {
            log.warn("停止录制失败，稍后重试 [{}]：{}", name, error.getMessage());
        }
        return Result.requeue(STOP_REQUEUE);
    }

    private void markRecorderLost(String name) {
        try {
            recordingStatus.fail(name, LiveErrorCode.LIVE_RECORDER_LOST, "录制器实例已失联").block(LOOKUP_TIMEOUT);
        } catch (RuntimeException error) {
            log.warn("标记录制失联失败 [{}]：{}", name, error.getMessage());
        }
    }

    private Result cleanup(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        try {
            purgeObjects(recording);
            deleteLocalFiles(recording);
            ExtensionUtil.removeFinalizers(recording.getMetadata(), Set.of(FINALIZER));
            client.update(recording);
            log.info("直播录制清理完成 [{}]", name);
            return Result.doNotRetry();
        } catch (RuntimeException error) {
            return recordFailure(name, error);
        }
    }

    // 先删对象再删本地，任一失败都保留 finalizer 与录制记录
    private void purgeObjects(LiveRecording recording) {
        for (var part : parts(recording)) {
            var fileId = part.getStorageFile();
            if (!StringUtils.hasText(fileId)) {
                continue;
            }
            if (part.getState() == PartState.UPLOADING) {
                storageTransfer.abortUpload(fileId).onErrorResume(error -> Mono.empty()).block(CLEANUP_TIMEOUT);
            }
            storageTransfer.purgeObjectFirst(fileId).block(CLEANUP_TIMEOUT);
        }
    }

    private void deleteLocalFiles(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        try {
            for (var part : parts(recording)) {
                Files.deleteIfExists(workspace.partTempPath(name, part.getIndex()));
                Files.deleteIfExists(workspace.partPath(name, part.getIndex()));
            }
            workspace.deleteRecordingDir(name);
        } catch (IOException error) {
            throw new IllegalStateException("删除录制本地文件失败：" + name, error);
        }
    }

    private Result recordFailure(String name, RuntimeException error) {
        metrics.cleanupFailed();
        log.warn("直播录制清理失败 [{}]：{}", name, error.getMessage());
        var next = currentAttempts(name) + 1;
        var attempts = next;
        try {
            var updated = recordingStatus
                    .mutate(name, status -> status.setCleanupAttempts(next))
                    .block(LOOKUP_TIMEOUT);
            if (updated != null && updated.getStatus() != null) {
                attempts = updated.getStatus().getCleanupAttempts();
            }
        } catch (RuntimeException updateError) {
            log.warn("记录清理失败次数失败 [{}]：{}", name, updateError.getMessage());
        }
        if (attempts > MAX_ATTEMPTS) {
            liveAlerts
                    .alert("live.cleanup.failed", name, Map.of(
                            "recordingName", name,
                            "errorCode", LiveErrorCode.LIVE_CLEANUP_FAILED.name()))
                    .onErrorResume(alertError -> Mono.empty())
                    .subscribe();
            return Result.requeue(SLOW_RETRY);
        }
        return Result.requeue(FAILURE_RETRY);
    }

    private int currentAttempts(String name) {
        return client.fetch(LiveRecording.class, name)
                .map(LiveRecording::getStatus)
                .map(LiveRecording.Status::getCleanupAttempts)
                .orElse(0);
    }

    // 本实例没有录制任务时，只有心跳缺失或过期才认为实例失联
    private static boolean isOwnerLost(LiveRecording recording) {
        var status = recording.getStatus();
        if (status == null || status.getHeartbeatAt() == null) {
            return true;
        }
        return status.getHeartbeatAt().plus(HEARTBEAT_TIMEOUT).isBefore(Instant.now());
    }

    private static boolean isActive(LiveRecordingState state) {
        return state == LiveRecordingState.QUEUED
                || state == LiveRecordingState.RECORDING
                || state == LiveRecordingState.FINALIZING;
    }

    private static List<LiveRecording.Part> parts(LiveRecording recording) {
        var status = recording.getStatus();
        if (status == null || status.getParts() == null) {
            return List.of();
        }
        return status.getParts();
    }
}