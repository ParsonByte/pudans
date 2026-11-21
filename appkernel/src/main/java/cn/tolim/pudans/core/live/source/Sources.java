package cn.tolim.pudans.core.live.source;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.contract.live.dto.LiveSourceHealth;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.exception.DuplicateNameException;
import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 直播源业务编排：创建、手动解析、启停、删除、立即录制
@Slf4j
@Component
@RequiredArgsConstructor
public class Sources {

    private final ReactiveExtensionClient client;

    private final LiveSourceValidator validator;

    private final RoomResolver roomResolver;

    private final SourceStatus sourceStatus;

    private final LiveSourceRegistry registry;

    private final SourceHealth sourceHealth;

    private final Recordings recordings;

    private final LiveProperties properties;

    public Mono<LiveSource> create(
            String roomUrl,
            String alias,
            Boolean autoRecord,
            Integer segmentSeconds,
            Integer mergeIntervalSeconds,
            String storagePolicy) {
        return Mono.fromCallable(() -> validator.parseRoomUrl(roomUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(room -> {
                    var name = LiveSourceValidator.resourceName(room);
                    var auto = autoRecord == null || autoRecord;
                    var segment = LiveSourceValidator.segmentSeconds(segmentSeconds);
                    var merge = LiveSourceValidator.mergeIntervalSeconds(mergeIntervalSeconds);
                    return validator
                            .ensureAbsent(name)
                            .then(Mono.defer(() -> validator.resolveStoragePolicy(autoRecord, storagePolicy)))
                            .zipWhen(policy -> roomResolver.resolve(
                                    room.platform(), room.roomId(), false))
                            .flatMap(pair -> createSource(
                                    name,
                                    room.platform(),
                                    room.roomId(),
                                    room.roomUrl(),
                                    alias,
                                    auto,
                                    segment,
                                    merge,
                                    pair.getT1().orElse(null),
                                    pair.getT2()));
                });
    }

    public Mono<LiveSource> resolve(String name, boolean force) {
        return client.fetch(LiveSource.class, name)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                .flatMap(source -> {
                    var spec = source.getSpec();
                    return roomResolver
                            .resolve(spec.getPlatform(), spec.getRoomId(), force)
                            .onErrorResume(ApiException.class, error -> sourceStatus
                                    .applyError(name, error.getCode(), error.getMessage())
                                    .then(Mono.<LiveRoomInfo>error(error)))
                            .flatMap(info -> info.live()
                                    ? sourceStatus.applyLive(name, info)
                                    : sourceStatus.applyOffline(
                                            name, LiveErrorCode.LIVE_ROOM_OFFLINE.message()));
                });
    }

    public Mono<LiveSource> setEnabled(String name, boolean enabled) {
        return client.fetch(LiveSource.class, name)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                .flatMap(source -> {
                    if (source.getSpec().isEnabled() == enabled) {
                        if (enabled) {
                            registry.upsert(source);
                        } else {
                            registry.remove(name);
                        }
                        return Mono.just(source);
                    }
                    source.getSpec().setEnabled(enabled);
                    return client.update(source).flatMap(updated -> enabled
                            ? sourceStatus.transition(
                                    name, LiveSourceStateMachine.Event.ENABLED, null)
                            : stopCurrentRecording(updated)
                                    .then(sourceStatus.transition(
                                            name,
                                            LiveSourceStateMachine.Event.DISABLED,
                                            null)));
                });
    }

    public Mono<LiveSource> delete(String name) {
        return client.fetch(LiveSource.class, name)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                .flatMap(source -> {
                    registry.remove(name);
                    return stopCurrentRecording(source).then(client.delete(source));
                });
    }

    public Mono<LiveRecording> recordOnce(String name) {
        return client.fetch(LiveSource.class, name)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                .flatMap(source -> {
                    var status = source.getStatus();
                    if (status.getState() == LiveSourceState.RECORDING
                            || StringUtils.hasText(status.getCurrentRecording())) {
                        return Mono.error(LiveErrorCode.LIVE_RECORDING_ACTIVE.exception());
                    }
                    var spec = source.getSpec();
                    return validator
                            .resolveStoragePolicy(Boolean.TRUE, spec.getStoragePolicy())
                            .flatMap(resolved -> {
                                var policy = resolved.orElseThrow(
                                        () -> LiveErrorCode.LIVE_STORAGE_POLICY_INVALID.exception());
                                Mono<LiveSource> ready = Mono.just(source);
                                if (!policy.equals(spec.getStoragePolicy())) {
                                    spec.setStoragePolicy(policy);
                                    ready = client.update(source);
                                }
                                return ready.then(roomResolver
                                        .resolve(spec.getPlatform(), spec.getRoomId(), true)
                                        .onErrorResume(ApiException.class, error -> sourceStatus
                                                .applyError(name, error.getCode(), error.getMessage())
                                                .then(Mono.<LiveRoomInfo>error(error)))
                                        .flatMap(info -> info.live()
                                                ? startManual(name, info)
                                                : sourceStatus
                                                        .applyOffline(
                                                                name,
                                                                LiveErrorCode.LIVE_ROOM_OFFLINE.message())
                                                        .then(Mono.<LiveRecording>error(
                                                                LiveErrorCode.LIVE_ROOM_OFFLINE.exception()))));
                            });
                });
    }

    public Mono<LiveSource> applyLive(String name, LiveRoomInfo info) {
        return sourceStatus.applyLive(name, info);
    }

    public Mono<LiveSource> applyOffline(String name, String message) {
        return sourceStatus.applyOffline(name, message);
    }

    public Mono<LiveSource> applyError(String name, String code, String message) {
        return sourceStatus.applyError(name, code, message);
    }

    public Mono<LiveSource> markRecording(String name, String recordingName) {
        return sourceStatus.markRecording(name, recordingName);
    }

    public Mono<LiveSourceHealth> preview(String roomUrl) {
        return Mono.fromCallable(() -> validator.parseRoomUrl(roomUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(room -> {
                    var name = LiveSourceValidator.resourceName(room);
                    return roomResolver
                            .resolve(room.platform(), room.roomId(), false)
                            .map(info -> sourceHealth.preview(name, info));
                });
    }

    private Mono<LiveRecording> startManual(String name, LiveRoomInfo info) {
        return sourceStatus
                .applyLive(name, info)
                .flatMap(live -> recordings.start(live, info, false, "api"))
                .flatMap(recording -> sourceStatus
                        .markRecording(name, recording.getMetadata().getName())
                        .thenReturn(recording));
    }

    private Mono<Void> stopCurrentRecording(LiveSource source) {
        var recording = source.getStatus().getCurrentRecording();
        if (!StringUtils.hasText(recording)) {
            return Mono.empty();
        }
        return recordings
                .stop(recording, "api")
                .onErrorResume(error -> {
                    log.warn("停止直播源关联录制失败：{} {}", recording, error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<LiveSource> createSource(
            String name,
            cn.tolim.pudans.contract.live.LivePlatform platform,
            String roomId,
            String roomUrl,
            String alias,
            boolean autoRecord,
            int segmentSeconds,
            int mergeIntervalSeconds,
            String storagePolicy,
            LiveRoomInfo info) {
        var now = Instant.now();
        var source = new LiveSource();
        var metadata = new Metadata();
        metadata.setName(name);
        source.setMetadata(metadata);
        var spec = source.getSpec();
        spec.setPlatform(platform);
        spec.setRoomId(roomId);
        spec.setRoomUrl(roomUrl);
        spec.setAlias(alias);
        spec.setEnabled(true);
        spec.setAutoRecord(autoRecord);
        spec.setSegmentSeconds(segmentSeconds);
        spec.setMergeIntervalSeconds(mergeIntervalSeconds);
        spec.setStoragePolicy(storagePolicy);
        var status = source.getStatus();
        status.setRoomTitle(info.title());
        status.setAnchorName(info.anchorName());
        status.setAnchorAvatar(info.anchorAvatar());
        status.setAnchorId(info.anchorId());
        status.setLive(info.live());
        status.setLiveStatus(info.liveStatus());
        status.setLastResolvedAt(info.resolvedAt() == null ? now : info.resolvedAt());
        var monitor = properties.getMonitor();
        if (info.live()) {
            status.setState(LiveSourceState.LIVE);
            status.setLastLiveAt(now);
            status.setLastStreamAt(now);
            status.setNextCheckAt(now.plus(monitor.getLiveInterval()));
        } else {
            status.setState(LiveSourceState.OFFLINE);
            status.setLastOfflineAt(now);
            status.setMessage(LiveErrorCode.LIVE_ROOM_OFFLINE.message());
            var interval = Duration.ofSeconds(Math.max(spec.getCheckIntervalSeconds(), 1));
            status.setNextCheckAt(now.plus(interval.compareTo(monitor.getDefaultInterval()) >= 0
                    ? interval
                    : monitor.getDefaultInterval()));
        }
        return client.create(source)
                .doOnNext(registry::upsert)
                .onErrorMap(
                        error -> error instanceof DuplicateNameException
                                || error instanceof DataIntegrityViolationException,
                        error -> LiveErrorCode.LIVE_SOURCE_DUPLICATE.exception());
    }
}
