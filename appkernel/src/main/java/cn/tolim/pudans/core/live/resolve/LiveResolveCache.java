package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
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

    // 注入 Redis、配置与指标组件
    public LiveResolveCache(
            ReactiveStringRedisTemplate redisTemplate, LiveProperties properties, LiveMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    // 读取解析缓存，Redis 异常降级为未命中
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

    // 在线与离线使用不同 TTL 写入解析结果
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

    // 读取限流冷却截止时间，异常按无冷却处理
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

    // 仅在截止时间未过时写入冷却键
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

    // 读取连续限流次数，缺失按零处理
    public Mono<Long> streak(LivePlatform platform, String roomId) {
        return redisTemplate
                .opsForValue()
                .get(rateLimitKey(platform, roomId))
                .map(Long::parseLong)
                .onErrorResume(error -> Mono.empty())
                .defaultIfEmpty(0L);
    }

    // 自增限流次数并设置上限为一小时
    public Mono<Long> incrementStreak(LivePlatform platform, String roomId) {
        var key = rateLimitKey(platform, roomId);
        return redisTemplate
                .opsForValue()
                .increment(key)
                .flatMap(value -> redisTemplate.expire(key, Duration.ofHours(1)).thenReturn(value))
                .onErrorResume(error -> Mono.just(1L));
    }

    // 解析成功后清除连续限流计数
    public Mono<Void> clearStreak(LivePlatform platform, String roomId) {
        return redisTemplate.delete(rateLimitKey(platform, roomId)).then();
    }

    // 抢解析锁，Redis 异常时降级为直接解析
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

    // 仅释放自己持有的解析锁
    public Mono<Void> releaseLock(LivePlatform platform, String roomId, String owner) {
        return redisTemplate
                .execute(RELEASE_SCRIPT, java.util.List.of(lockKey(platform, roomId)), owner)
                .then()
                .onErrorResume(error -> Mono.empty());
    }

    // 解析缓存键按平台与房间隔离
    private static String resolveKey(LivePlatform platform, String roomId) {
        return PREFIX + "resolve:" + platform.name().toLowerCase() + ":" + roomId;
    }

    // 解析锁键与缓存键分开命名
    private static String lockKey(LivePlatform platform, String roomId) {
        return PREFIX + "resolve:lock:" + platform.name().toLowerCase() + ":" + roomId;
    }

    // 冷却键记录平台限流的恢复时间
    private static String cooldownKey(LivePlatform platform, String roomId) {
        return PREFIX + "cooldown:" + platform.name().toLowerCase() + ":" + roomId;
    }

    // 限流计数键按平台与房间隔离
    private static String rateLimitKey(LivePlatform platform, String roomId) {
        return PREFIX + "ratelimit:streak:" + platform.name().toLowerCase() + ":" + roomId;
    }
}
