package cn.tolim.pudans.core.live.monitor;

import cn.tolim.pudans.core.infra.properties.LiveProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 直播监控的 Redis leader 租约，只有 leader 执行解析和自动录制
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveLeaderLease {

    private static final String KEY = "pudans:live:leader";

    private static final RedisScript<Long> RENEW_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final LiveProperties properties;

    private final String instanceId = UUID.randomUUID().toString();

    private final AtomicBoolean leader = new AtomicBoolean();

    public boolean isLeader() {
        return leader.get();
    }

    public Mono<Boolean> renew() {
        var ttl = properties.getMonitor().getLeaderTtl();
        return redisTemplate
                .opsForValue()
                .setIfAbsent(KEY, instanceId, ttl)
                .flatMap(acquired -> Boolean.TRUE.equals(acquired) ? Mono.just(true) : renewExisting(ttl))
                .doOnNext(leader::set)
                .onErrorResume(error -> {
                    log.warn("直播监控 leader 租约续期失败：{}", error.getMessage());
                    leader.set(false);
                    return Mono.just(false);
                });
    }

    public Mono<Void> release() {
        return redisTemplate
                .execute(RELEASE_SCRIPT, List.of(KEY), instanceId)
                .then()
                .doFinally(ignored -> leader.set(false))
                .onErrorResume(error -> {
                    log.warn("直播监控 leader 租约释放失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Boolean> renewExisting(Duration ttl) {
        return redisTemplate
                .execute(RENEW_SCRIPT, List.of(KEY), instanceId, String.valueOf(ttl.toMillis()))
                .next()
                .map(value -> value != null && value > 0)
                .defaultIfEmpty(false);
    }
}
