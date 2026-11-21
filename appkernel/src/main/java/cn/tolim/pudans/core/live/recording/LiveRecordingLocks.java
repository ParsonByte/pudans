package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_ACTIVE;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 用 Redis 锁串行化同一直播源的录制启动，避免多实例重复录制。
@Component
@RequiredArgsConstructor
public class LiveRecordingLocks {

    private static final String KEY_PREFIX = "pudans:live:recording-lock:";

    private static final Duration LOCK_TTL = Duration.ofMinutes(1);

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    // 获取锁后执行动作，未获取到锁时返回录制已激活。
    public <T> Mono<T> withLock(String sourceName, Mono<T> action) {
        var key = KEY_PREFIX + sourceName;
        var token = UUID.randomUUID().toString();
        return Mono.usingWhen(
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(key, token, LOCK_TTL)
                                .defaultIfEmpty(false)
                                .flatMap(acquired -> acquired ? Mono.just(token) : Mono.empty()),
                        ignored -> action,
                        ignored -> release(key, token),
                        (ignored, error) -> release(key, token),
                        ignored -> release(key, token))
                .switchIfEmpty(Mono.error(LIVE_RECORDING_ACTIVE.exception()));
    }

    // 用 Lua 原子比对令牌后释放锁，避免误删其他实例的锁。
    private Mono<Void> release(String key, String token) {
        return redisTemplate
                .execute(RELEASE_SCRIPT, List.of(key), token)
                .then()
                .onErrorResume(error -> Mono.empty());
    }
}
