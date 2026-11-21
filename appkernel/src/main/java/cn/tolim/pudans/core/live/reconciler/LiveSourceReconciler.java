package cn.tolim.pudans.core.live.reconciler;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.ReactiveReconciler;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.Recordings;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.live.source.LiveSourceRegistry;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.core.live.source.LiveSourceStateMachine;

// 直播源协调器：维护 finalizer、驱动启停状态、删除前停止关联录制并清理
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSourceReconciler implements ReactiveReconciler<Reconciler.Request> {

    public static final String FINALIZER_NAME = "pudans.tolim.cn/live-source-cleanup";

    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration CLEANUP_REQUEUE = Duration.ofSeconds(5);

    private static final Set<LiveRecordingState> ACTIVE_STATES =
            Set.of(LiveRecordingState.QUEUED, LiveRecordingState.RECORDING, LiveRecordingState.FINALIZING);

    private final InternalReactiveExtensionClient client;

    private final LiveSourceRegistry registry;

    private final SourceStatus sourceStatus;

    private final Recordings recordingService;

    private final LiveAlerts liveAlerts;

    private final LiveMetrics metrics;

    // 绑定直播源扩展类型到协调器
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new LiveSource()).build();
    }

    // 已删除的源走清理，其余同步 finalizer 与启用状态
    @Override
    public Mono<Result> reconcileReactive(Reconciler.Request request) {
        return client.fetch(LiveSource.class, request.name())
                .flatMap(source -> isDeleted(source)
                        ? cleanup(source)
                        : reconcileActive(source))
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    // 补齐 finalizer 后同步启用状态
    private Mono<Result> reconcileActive(LiveSource source) {
        var name = source.getMetadata().getName();
        var added = addFinalizers(source.getMetadata(), Set.of(FINALIZER_NAME));
        var persisted = added ? client.updateInternal(source) : Mono.just(source);
        return persisted
                .flatMap(updated -> syncEnabled(updated).thenReturn(Result.doNotRetry()))
                .onErrorResume(error -> {
                    log.warn("直播源协调失败：{} {}", name, error.getMessage());
                    return Mono.just(Result.doNotRetry());
                });
    }

    // 启用时加入注册表，停用时移除并停止录制
    private Mono<Void> syncEnabled(LiveSource source) {
        var name = source.getMetadata().getName();
        if (source.getSpec().isEnabled()) {
            registry.upsert(source);
            var state = source.getStatus().getState();
            if (state == LiveSourceState.IDLE || state == LiveSourceState.DISABLED) {
                return sourceStatus
                        .transition(name, LiveSourceStateMachine.Event.ENABLED, null)
                        .then();
            }
            return Mono.empty();
        }
        registry.remove(name);
        return stopCurrentRecording(source)
                .then(sourceStatus
                        .transition(name, LiveSourceStateMachine.Event.DISABLED, null)
                        .then());
    }

    // 停用或删除前停止该源关联的录制
    private Mono<Void> stopCurrentRecording(LiveSource source) {
        var recording = source.getStatus().getCurrentRecording();
        if (!StringUtils.hasText(recording)) {
            return Mono.empty();
        }
        return recordingService
                .stop(recording, "reconciler")
                .onErrorResume(error -> {
                    log.warn("停止直播源关联录制失败：{} {}", recording, error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // 删除中的源先停止活动录制，再移除 finalizer
    private Mono<Result> cleanup(LiveSource source) {
        var name = source.getMetadata().getName();
        registry.remove(name);
        var deletedAt = source.getMetadata().getDeletionTimestamp();
        var deadline = (deletedAt == null ? Instant.now() : deletedAt).plus(CLEANUP_TIMEOUT);
        var options = ListOptions.builder().andQuery(equal("spec.sourceName", name)).build();
        return client.listAll(LiveRecording.class, options, defaultSort())
                .filter(recording -> !isDeleted(recording)
                        && recording.getStatus().getState() != null
                        && ACTIVE_STATES.contains(recording.getStatus().getState()))
                .collectList()
                .flatMap(recordings -> {
                    if (recordings.isEmpty()) {
                        return removeFinalizer(source).thenReturn(Result.doNotRetry());
                    }
                    return stopAll(recordings).then(Mono.defer(() -> {
                        if (Instant.now().isBefore(deadline)) {
                            return Mono.just(Result.requeue(CLEANUP_REQUEUE));
                        }
                        var payload = new LinkedHashMap<String, Object>();
                        payload.put("sourceName", name);
                        payload.put("recordings", recordings.stream()
                                .map(recording -> recording.getMetadata().getName())
                                .toList());
                        payload.put("errorCode", LiveErrorCode.LIVE_STOP_TIMEOUT.name());
                        payload.put("message", "直播源删除时录制停止超时");
                        payload.put("at", Instant.now().toString());
                        metrics.cleanupFailed();
                        return liveAlerts
                                .alert("live.cleanup.failed", name, payload)
                                .then(removeFinalizer(source))
                                .thenReturn(Result.doNotRetry());
                    }));
                })
                .onErrorResume(error -> {
                    log.warn("直播源删除清理失败：{} {}", name, error.getMessage());
                    return Mono.just(Result.requeue(CLEANUP_REQUEUE));
                });
    }

    // 并发停止该源下所有活动录制
    private Mono<Void> stopAll(List<LiveRecording> recordings) {
        return Flux.fromIterable(recordings)
                .flatMap(recording -> {
                    var recordingName = recording.getMetadata().getName();
                    return recordingService
                            .stop(recordingName, "source-delete")
                            .onErrorResume(error -> {
                                log.warn("删除直播源时停止录制失败：{} {}", recordingName, error.getMessage());
                                return Mono.empty();
                            });
                })
                .then();
    }

    // 仅在 finalizer 移除成功后才写回资源
    private Mono<Void> removeFinalizer(LiveSource source) {
        var removed = removeFinalizers(source.getMetadata(), Set.of(FINALIZER_NAME));
        if (!removed) {
            return Mono.empty();
        }
        return client.updateInternal(source).then();
    }
}
