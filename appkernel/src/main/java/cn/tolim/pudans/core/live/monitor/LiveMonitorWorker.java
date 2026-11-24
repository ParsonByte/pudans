package cn.tolim.pudans.core.live.monitor;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.live.resolve.LiveResolveCache;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.source.LiveSourceRegistry;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 开播监控 worker：按 leader 租约定时解析到期直播源并驱动状态与自动录制
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveMonitorWorker {

    private final LiveProperties properties;

    private final LiveSourceRegistry registry;

    private final SourceStatus sourceStatus;

    private final RoomResolver roomResolver;

    private final Recordings recordings;

    private final LiveResolveCache resolveCache;

    private final LiveAlerts liveAlerts;

    private final LiveMetrics metrics;

    private final LiveLeaderLease lease;

    private Disposable subscription;

    // 按配置间隔启动监控循环，丢弃积压的 tick
    @PostConstruct
    public void start() {
        var tick = properties.getMonitor().getTick();
        subscription = Flux.interval(tick)
                .onBackpressureDrop()
                .concatMap(index -> tick().onErrorResume(error -> {
                    log.warn("开播监控 tick 失败：{}", error.getMessage());
                    return Mono.empty();
                }))
                .subscribe();
    }

    // 停止监控循环并释放订阅
    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    // 一次 tick 只做租约、到期选取与限流并发解析，单源失败不影响其他源
    public Mono<Void> tick() {
        var started = Instant.now();
        return Mono.defer(() -> lease
                        .renew()
                        .flatMap(leader -> metrics
                                .refreshFailedCount(leader)
                                .then(leader ? runDue() : Mono.<Void>empty()))
                        .doFinally(signal -> metrics.monitorTick(Duration.between(started, Instant.now())))
                        .onErrorResume(error -> {
                            log.warn("开播监控 tick 异常：{}", error.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    // 取到期源并按并发上限解析，避免一次 tick 过载
    private Mono<Void> runDue() {
        var due = registry.snapshotDue(Instant.now(), properties.getMonitor().getMaxPerTick());
        if (due.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(due)
                .flatMap(this::check, properties.getMonitor().getResolveConcurrency())
                .then();
    }

    // 解析单个源并按在线、离线或失败分支处理
    private Mono<Void> check(LiveSourceRegistry.Snapshot snapshot) {
        var name = snapshot.name();
        return Mono.defer(() -> roomResolver
                        .resolve(snapshot.platform(), snapshot.roomId(), true)
                        .flatMap(info -> info.live()
                                ? handleLive(name, info)
                                : handleOffline(name))
                        .onErrorResume(error -> handleFailure(snapshot, error)))
                .doFinally(signal -> registry.release(name));
    }

    // 写回在线状态并按配置启动自动录制
    private Mono<Void> handleLive(String name, LiveRoomInfo info) {
        return sourceStatus.applyLive(name, info).flatMap(source -> startAutoRecording(source, info));
    }

    // 仅未录制且开启自动录制的源才发起录制
    private Mono<Void> startAutoRecording(LiveSource source, LiveRoomInfo info) {
        var status = source.getStatus();
        if (!source.getSpec().isAutoRecord() || StringUtils.hasText(status.getCurrentRecording())) {
            return Mono.empty();
        }
        var name = source.getMetadata().getName();
        return recordings
                .start(source, info, true, "monitor")
                .flatMap(recording -> recording.getStatus().getState()
                                == cn.tolim.pudans.contract.live.LiveRecordingState.FAILED
                        ? sourceStatus
                                .recordingFailed(
                                        name,
                                        LiveErrorCode.LIVE_STREAM_FAILED.name(),
                                        recording.getStatus().getMessage() == null
                                                ? "录制启动失败。"
                                                : recording.getStatus().getMessage())
                                .then()
                        : sourceStatus.markRecording(name, recording.getMetadata().getName()))
                .then()
                .onErrorResume(error -> handleRecordingFailure(name, error));
    }

    // 并发上限只告警，其余失败写回源并发送告警
    private Mono<Void> handleRecordingFailure(String sourceName, Throwable error) {
        var code = error instanceof ApiException api
                ? api.getCode()
                : LiveErrorCode.LIVE_STREAM_FAILED.name();
        if (LiveErrorCode.LIVE_RECORDING_LIMIT_REACHED.name().equals(code)) {
            log.warn("直播源自动录制达到并发上限：{}", sourceName);
            return Mono.empty();
        }
        log.warn("直播源自动录制失败：{} {}", sourceName, error.getMessage());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sourceName", sourceName);
        payload.put("errorCode", code);
        payload.put("message", error.getMessage());
        payload.put("at", Instant.now().toString());
        return sourceStatus
                .recordingFailed(sourceName, code, error.getMessage())
                .then(liveAlerts.alert("live.recording.failed", sourceName, payload))
                .then();
    }

    // 写回离线状态并停止该源正在进行的录制
    private Mono<Void> handleOffline(String name) {
        return sourceStatus
                .applyOffline(name, LiveErrorCode.LIVE_ROOM_OFFLINE.message())
                .flatMap(source -> {
                    var recording = source.getStatus().getCurrentRecording();
                    if (!StringUtils.hasText(recording)) {
                        return Mono.empty();
                    }
                    return recordings
                            .stop(recording, "monitor")
                            .onErrorResume(error -> {
                                log.warn("下线停止录制失败：{} {}", recording, error.getMessage());
                                return Mono.empty();
                            })
                            .then(sourceStatus.update(name, current ->
                                    current.getStatus().setCurrentRecording(null)))
                            .then();
                });
    }

    // 写回解析错误，限流达到阈值时发送告警
    private Mono<Void> handleFailure(LiveSourceRegistry.Snapshot snapshot, Throwable error) {
        var name = snapshot.name();
        var code = error instanceof ApiException api
                ? api.getCode()
                : LiveErrorCode.LIVE_RESOLVE_FAILED.name();
        var message = error.getMessage() == null ? code : error.getMessage();
        var applied = sourceStatus.applyError(name, code, message);
        if (!LiveErrorCode.LIVE_RATE_LIMITED.name().equals(code)) {
            return applied.then();
        }
        return applied
                .then(resolveCache
                        .streak(snapshot.platform(), snapshot.roomId())
                        .defaultIfEmpty(0L)
                        .flatMap(streak -> streak < properties.getAlert().getRateLimitThreshold()
                                ? Mono.<Void>empty()
                                : liveAlerts
                                        .rateLimited(
                                                snapshot.platform(),
                                                snapshot.roomId(),
                                                Instant.now().plus(properties.getResolve().getCooldown()),
                                                streak)
                                        .then(Mono.<Void>empty())))
                .then();
    }
}
