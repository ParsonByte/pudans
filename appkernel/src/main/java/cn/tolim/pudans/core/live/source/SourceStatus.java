package cn.tolim.pudans.core.live.source;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.event.live.LiveSourceStateChangedEvent;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.live.resolve.LiveResolveCache;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// 直播源状态写回唯一入口：版本冲突重试、状态事件发布、注册表同步
@Slf4j
@Component
@RequiredArgsConstructor
public class SourceStatus {

    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMillis(50), Duration.ofMillis(150), Duration.ofMillis(300));

    private final ReactiveExtensionClient client;

    private final LiveSourceStateMachine stateMachine;

    private final LiveSourceRegistry registry;

    private final LiveResolveCache resolveCache;

    private final DomainEventPublisher domainEventPublisher;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final LiveProperties properties;

    public Mono<LiveSource> update(String name, Consumer<LiveSource> mutation) {
        return mutate(name, "update", mutation);
    }

    public Mono<LiveSource> transition(
            String name, LiveSourceStateMachine.Event event, String message) {
        return mutate(name, reasonOf(event), source -> applyEvent(source, event, message));
    }

    public Mono<LiveSource> applyLive(String name, LiveRoomInfo info) {
        return mutate(name, "resolve_online", source -> {
            var now = Instant.now();
            applyEvent(source, LiveSourceStateMachine.Event.RESOLVED_LIVE, null);
            var status = source.getStatus();
            status.setRoomTitle(info.title());
            status.setAnchorName(info.anchorName());
            status.setAnchorAvatar(info.anchorAvatar());
            status.setAnchorId(info.anchorId());
            status.setLive(true);
            status.setLiveStatus(info.liveStatus());
            status.setLastResolvedAt(info.resolvedAt() == null ? now : info.resolvedAt());
            status.setLastLiveAt(now);
            status.setLastStreamAt(now);
            status.setResolveFailures(0);
            status.setRateLimited(false);
            status.setCooldownUntil(null);
            status.setLastResolveCode(null);
            status.setLastResolveError(null);
            status.setMessage(null);
            status.setNextCheckAt(now.plus(properties.getMonitor().getLiveInterval()));
        });
    }

    public Mono<LiveSource> applyOffline(String name, String message) {
        return mutate(name, "resolve_offline", source -> {
            var now = Instant.now();
            applyEvent(source, LiveSourceStateMachine.Event.RESOLVED_OFFLINE, message);
            var status = source.getStatus();
            status.setLive(false);
            status.setLastResolvedAt(now);
            status.setLastOfflineAt(now);
            status.setResolveFailures(0);
            status.setRateLimited(false);
            status.setCooldownUntil(null);
            status.setLastResolveCode(null);
            status.setLastResolveError(null);
            status.setMessage(message);
            status.setNextCheckAt(now.plus(offlineInterval(source)));
        });
    }

    public Mono<LiveSource> applyError(String name, String code, String message) {
        if (LiveErrorCode.LIVE_RATE_LIMITED.name().equals(code)) {
            return client.fetch(LiveSource.class, name)
                    .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                    .flatMap(source -> resolveCache
                            .cooldownUntil(
                                    source.getSpec().getPlatform(), source.getSpec().getRoomId())
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(cooldown -> {
                                var until = cooldown.orElseGet(() -> Instant.now()
                                        .plus(properties.getResolve().getCooldown()));
                                return mutate(name, "resolve_rate_limited", current -> {
                                    var status = current.getStatus();
                                    status.setRateLimited(true);
                                    status.setLastResolveCode(code);
                                    status.setLastResolveError(message);
                                    status.setMessage(message);
                                    status.setCooldownUntil(until);
                                    status.setNextCheckAt(until.plusSeconds(30));
                                });
                            }));
        }
        return mutate(name, "resolve_failed", source -> {
            var now = Instant.now();
            var status = source.getStatus();
            var failures = status.getResolveFailures() + 1;
            status.setResolveFailures(failures);
            status.setLastResolveCode(code);
            status.setLastResolveError(message);
            status.setMessage(message);
            if (failures >= properties.getMonitor().getErrorThreshold()) {
                applyEvent(source, LiveSourceStateMachine.Event.RESOLVE_FAILED, message);
                status.setNextCheckAt(now.plus(failureBackoff(failures)));
            } else {
                status.setNextCheckAt(now.plus(properties.getMonitor().getFailureBackoff()));
            }
        });
    }

    public Mono<LiveSource> markRecording(String name, String recordingName) {
        return mutate(name, "recording_started", source -> {
            var now = Instant.now();
            applyEvent(source, LiveSourceStateMachine.Event.RECORDING_STARTED, null);
            var status = source.getStatus();
            status.setCurrentRecording(recordingName);
            status.setLive(true);
            status.setLastStreamAt(now);
            status.setMessage(null);
            status.setNextCheckAt(now.plus(properties.getMonitor().getLiveInterval()));
        });
    }

    public Mono<LiveSource> recordingFailed(String name, String code, String message) {
        return mutate(name, "recording_failed", source -> {
            var now = Instant.now();
            applyEvent(source, LiveSourceStateMachine.Event.RECORDING_FAILED, message);
            var status = source.getStatus();
            status.setCurrentRecording(null);
            status.setLastResolveCode(code);
            status.setLastResolveError(message);
            status.setMessage(message);
            status.setNextCheckAt(now.plus(properties.getMonitor().getFailureBackoff()));
        });
    }

    private Mono<LiveSource> mutate(String name, String reason, Consumer<LiveSource> mutation) {
        if (!StringUtils.hasText(name)) {
            return Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception());
        }
        return retryOnConflict(Mono.defer(() -> client.fetch(LiveSource.class, name)
                        .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                        .flatMap(source -> {
                            var from = source.getStatus().getState();
                            mutation.accept(source);
                            var to = source.getStatus().getState();
                            return client.update(source)
                                    .map(updated -> new Applied(updated, from, to, reason));
                        })))
                .doOnNext(this::afterApply)
                .map(Applied::source);
    }

    private void applyEvent(LiveSource source, LiveSourceStateMachine.Event event, String message) {
        var status = source.getStatus();
        var transition = stateMachine.next(status.getState(), event);
        status.setState(transition.to());
        if (message != null) {
            status.setMessage(message);
        }
        if (event == LiveSourceStateMachine.Event.ENABLED) {
            status.setNextCheckAt(Instant.now());
        }
        if (event == LiveSourceStateMachine.Event.DISABLED) {
            status.setNextCheckAt(null);
            status.setCurrentRecording(null);
        }
    }

    private void afterApply(Applied applied) {
        var source = applied.source();
        registry.upsert(source);
        if (applied.from() == applied.to() || applied.to() == null) {
            return;
        }
        var name = source.getMetadata().getName();
        var roomId = source.getSpec().getRoomId();
        var at = Instant.now();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sourceName", name);
        payload.put("roomId", roomId);
        payload.put("from", applied.from().name());
        payload.put("to", applied.to().name());
        payload.put("reason", applied.reason());
        payload.put("at", at.toString());
        try {
            domainEventPublisher.publish("live.source.state.changed", payload, null);
        } catch (RuntimeException error) {
            log.warn("发布直播源状态事件失败：{}", error.getMessage());
        }
        try {
            applicationEventPublisher.publishEvent(new LiveSourceStateChangedEvent(
                    name, roomId, applied.from(), applied.to(), applied.reason(), at));
        } catch (RuntimeException error) {
            log.warn("发布直播源状态变更事件失败：{}", error.getMessage());
        }
    }

    private Duration offlineInterval(LiveSource source) {
        var checkInterval = Duration.ofSeconds(Math.max(source.getSpec().getCheckIntervalSeconds(), 1));
        var defaultInterval = properties.getMonitor().getDefaultInterval();
        return checkInterval.compareTo(defaultInterval) >= 0 ? checkInterval : defaultInterval;
    }

    private Duration failureBackoff(int failures) {
        var threshold = properties.getMonitor().getErrorThreshold();
        var exponent = Math.min(Math.max(failures - threshold, 0), 10);
        var base = properties.getMonitor().getFailureBackoff().toMillis();
        var max = properties.getMonitor().getMaxFailureBackoff().toMillis();
        return Duration.ofMillis(Math.min(base * (1L << exponent), max));
    }

    private <T> Mono<T> retryOnConflict(Mono<T> source) {
        return source.retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
            var failure = signal.failure();
            if (!(failure instanceof OptimisticLockingFailureException)) {
                return Mono.<Long>error(failure);
            }
            var attempt = signal.totalRetries();
            if (attempt >= RETRY_DELAYS.size()) {
                log.warn("直播源状态写回冲突重试耗尽：{}", failure.getMessage());
                return Mono.<Long>error(failure);
            }
            return Mono.delay(RETRY_DELAYS.get((int) attempt));
        })));
    }

    private static String reasonOf(LiveSourceStateMachine.Event event) {
        return switch (event) {
            case ENABLED -> "enabled";
            case DISABLED -> "disabled";
            case RESOLVED_LIVE -> "resolve_online";
            case RESOLVED_OFFLINE -> "resolve_offline";
            case RESOLVE_FAILED -> "resolve_failed";
            case RECORDING_STARTED -> "recording_started";
            case RECORDING_ENDED -> "recording_ended";
            case RECORDING_FAILED -> "recording_failed";
        };
    }

    private record Applied(
            LiveSource source, LiveSourceState from, LiveSourceState to, String reason) {}
}
