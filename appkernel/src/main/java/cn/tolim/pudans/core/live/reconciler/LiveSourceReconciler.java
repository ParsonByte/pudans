package cn.tolim.pudans.core.live.reconciler;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.live.resource.LiveRecording;
import cn.tolim.pudans.contract.live.resource.LiveRecordingState;
import cn.tolim.pudans.contract.live.resource.LiveSource;
import cn.tolim.pudans.contract.live.resource.LiveSourceState;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.ReactiveReconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
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

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new LiveSource()).build();
    }

    @Override
    public Mono<Result> reconcileReactive(Reconciler.Request request) {
        return client.fetch(LiveSource.class, request.name())
                .flatMap(source -> isDeleted(source)
                        ? cleanup(source)
                        : reconcileActive(source))
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

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

    private Mono<Void> removeFinalizer(LiveSource source) {
        var removed = removeFinalizers(source.getMetadata(), Set.of(FINALIZER_NAME));
        if (!removed) {
            return Mono.empty();
        }
        return client.updateInternal(source).then();
    }
}
