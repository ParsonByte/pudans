package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RoomResolver {

    private final LiveRoomLinkParser linkParser;

    private final DouyinLiveRoomResolver douyinResolver;

    private final LiveResolveCache cache;

    private final LiveProperties properties;

    private final LiveMetrics metrics;

    private final String instanceId = UUID.randomUUID().toString();

    public RoomResolver(
            LiveRoomLinkParser linkParser,
            DouyinLiveRoomResolver douyinResolver,
            LiveResolveCache cache,
            LiveProperties properties,
            LiveMetrics metrics) {
        this.linkParser = linkParser;
        this.douyinResolver = douyinResolver;
        this.cache = cache;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Mono<LiveRoomInfo> resolveByUrl(String roomUrl, boolean force) {
        return Mono.fromCallable(() -> linkParser.parse(roomUrl))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMap(parsed -> resolve(parsed.platform(), parsed.roomId(), force));
    }

    public Mono<LiveRoomInfo> resolve(LivePlatform platform, String roomId, boolean force) {
        var cached = force ? Mono.<java.util.Optional<LiveRoomInfo>>just(java.util.Optional.empty())
                : cache.cached(platform, roomId);
        return cached.flatMap(found -> {
            if (found.isPresent()) {
                metrics.resolveCacheHit(platform.name());
                return Mono.just(found.get());
            }
            return cache.cooldownUntil(platform, roomId).flatMap(cooldown -> {
                if (cooldown.isPresent() && cooldown.get().isAfter(Instant.now())) {
                    metrics.resolveRateLimited(platform.name());
                    metrics.resolveFail(platform.name(), LiveErrorCode.LIVE_RATE_LIMITED.name());
                    return Mono.error(LiveErrorCode.LIVE_RATE_LIMITED.exception());
                }
                return acquireAndResolve(platform, roomId);
            });
        });
    }

    private Mono<LiveRoomInfo> acquireAndResolve(LivePlatform platform, String roomId) {
        return cache.acquireLock(platform, roomId, instanceId).flatMap(acquired -> {
            if (acquired) {
                return resolveDirect(platform, roomId)
                        .doFinally(signal -> cache.releaseLock(platform, roomId, instanceId).subscribe());
            }
            return waitForCache(platform, roomId);
        });
    }

    private Mono<LiveRoomInfo> waitForCache(LivePlatform platform, String roomId) {
        return Flux.interval(Duration.ofMillis(100))
                .take(properties.getResolve().getLockWait().toMillis() / 100)
                .concatMap(ignored -> cache.cached(platform, roomId))
                .filter(java.util.Optional::isPresent)
                .next()
                .map(java.util.Optional::get)
                .doOnNext(ignored -> metrics.resolveCacheHit(platform.name()))
                .switchIfEmpty(Mono.defer(() -> resolveDirect(platform, roomId)));
    }

    private Mono<LiveRoomInfo> resolveDirect(LivePlatform platform, String roomId) {
        return douyinResolver
                .resolve(roomId)
                .flatMap(info -> cache.cache(platform, roomId, info)
                        .then(cache.clearStreak(platform, roomId))
                        .thenReturn(info))
                .doOnNext(info -> metrics.resolveSuccess(platform.name()))
                .onErrorResume(error -> handleFailure(platform, roomId, error));
    }

    private Mono<LiveRoomInfo> handleFailure(LivePlatform platform, String roomId, Throwable error) {
        if (error instanceof ApiException apiException) {
            var code = apiException.getCode();
            if (LiveErrorCode.LIVE_RATE_LIMITED.name().equals(code)) {
                metrics.resolveRateLimited(platform.name());
                return cache.incrementStreak(platform, roomId).flatMap(streak -> {
                    var until = cooldownUntil(streak);
                    return cache.setCooldown(platform, roomId, until)
                            .then(Mono.error(LiveErrorCode.LIVE_RATE_LIMITED.exception()));
                });
            }
            metrics.resolveFail(platform.name(), code);
            return Mono.error(apiException);
        }
        metrics.resolveFail(platform.name(), LiveErrorCode.LIVE_RESOLVE_FAILED.name());
        log.warn("直播解析异常：platform={}，reason={}", platform, error.getMessage());
        return Mono.error(LiveErrorCode.LIVE_RESOLVE_FAILED.exception());
    }

    private Instant cooldownUntil(long streak) {
        var base = properties.getResolve().getCooldown().toMillis();
        var max = properties.getResolve().getMaxCooldown().toMillis();
        var factor = Math.min(Math.max(streak - 1, 0), 4);
        var millis = Math.min(base * (1L << factor), max);
        return Instant.now().plusMillis(millis);
    }
}
