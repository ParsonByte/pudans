package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.core.extension.live.LivePlatform;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.contract.live.LiveRoomInfo;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LiveResolveCache {

    private static final String PREFIX = "pudans:live:";

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final LiveProperties properties;

    private final LiveMetrics metrics;

    public LiveResolveCache(
            ReactiveStringRedisTemplate redisTemplate, LiveProperties properties, LiveMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Mono<Optional<LiveRoomInfo>> cached(LivePlatform platform, String roomId) {
        return redisTemplate
                .opsForValue()
                .get(resolveKey(platform, roomId))
                .flatMap(json -> Mono.fromCallable(() -> Optional.of(JsonUtils.jsonToObject(json, LiveRoomInfo.class))))
                .onErrorResume(error -> {
                    log.warn("直播解析缓存读取失败：{}", error.getMessage());
                    metrics.resolveRedisDegraded(platform.name());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Void> cache(LivePlatform platform, String roomId, LiveRoomInfo info) {
        var ttl = info.live() ? properties.getResolve().getCacheTtl() : properties.getResolve().getOfflineCacheTtl();
        return redisTemplate
                .opsForValue()
                .set(resolveKey(platform, roomId), JsonUtils.objectToJson(info), ttl)
                .then()
                .onErrorResume(error -> {
                    log.warn("直播解析缓存写入失败：{}", error.getMessage());
                    metrics.resolveRedisDegraded(platform.name());
                    return Mono.empty();
                });
    }

    public Mono<Optional<Instant>> cooldownUntil(LivePlatform platform, String roomId) {
        return redisTemplate
                .opsForValue()
                .get(cooldownKey(platform, roomId))
                .flatMap(value -> Mono.fromCallable(() -> Optional.of(Instant.ofEpochMilli(Long.parseLong(value)))))
                .onErrorResume(error -> {
                    log.warn("直播冷却读取失败：{}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Instant> setCooldown(LivePlatform platform, String roomId, Instant until) {
        var ttl = Duration.between(Instant.now(), until);
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.just(until);
        }
        return redisTemplate
                .opsForValue()
                .set(cooldownKey(platform, roomId), String.valueOf(until.toEpochMilli()), ttl)
                .thenReturn(until)
                .onErrorResume(error -> {
                    log.warn("直播冷却写入失败：{}", error.getMessage());
                    return Mono.just(until);
                });
    }

    public Mono<Long> streak(LivePlatform platform, String roomId) {
        return redisTemplate
                .opsForValue()
                .get(rateLimitKey(platform, roomId))
                .map(Long::parseLong)
                .onErrorResume(error -> Mono.empty())
                .defaultIfEmpty(0L);
    }

    public Mono<Long> incrementStreak(LivePlatform platform, String roomId) {
        var key = rateLimitKey(platform, roomId);
        return redisTemplate
                .opsForValue()
                .increment(key)
                .flatMap(value -> redisTemplate.expire(key, Duration.ofHours(1)).thenReturn(value))
                .onErrorResume(error -> Mono.just(1L));
    }

    public Mono<Void> clearStreak(LivePlatform platform, String roomId) {
        return redisTemplate.delete(rateLimitKey(platform, roomId)).then();
    }

    public Mono<Boolean> acquireLock(LivePlatform platform, String roomId, String owner) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey(platform, roomId), owner, properties.getResolve().getLockTtl())
                .onErrorResume(error -> {
                    log.warn("直播解析锁获取失败，降级为直接解析：{}", error.getMessage());
                    metrics.resolveRedisDegraded(platform.name());
                    return Mono.just(Boolean.TRUE);
                });
    }

    public Mono<Void> releaseLock(LivePlatform platform, String roomId, String owner) {
        return redisTemplate
                .execute(RELEASE_SCRIPT, java.util.List.of(lockKey(platform, roomId)), owner)
                .then()
                .onErrorResume(error -> Mono.empty());
    }

    private static String resolveKey(LivePlatform platform, String roomId) {
        return PREFIX + "resolve:" + platform.name().toLowerCase() + ":" + roomId;
    }

    private static String lockKey(LivePlatform platform, String roomId) {
        return PREFIX + "resolve:lock:" + platform.name().toLowerCase() + ":" + roomId;
    }

    private static String cooldownKey(LivePlatform platform, String roomId) {
        return PREFIX + "cooldown:" + platform.name().toLowerCase() + ":" + roomId;
    }

    private static String rateLimitKey(LivePlatform platform, String roomId) {
        return PREFIX + "ratelimit:streak:" + platform.name().toLowerCase() + ":" + roomId;
    }
}
