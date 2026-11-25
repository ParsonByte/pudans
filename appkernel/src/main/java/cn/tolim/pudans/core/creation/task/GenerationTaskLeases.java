package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GenerationTaskLeases {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(90);

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final ReactiveRedisTokenLease leases;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final Duration ttl;

    private final Duration heartbeatInterval;

    public GenerationTaskLeases(
            ReactiveRedisTokenLease leases, ReactiveStringRedisTemplate redisTemplate) {
        this(leases, redisTemplate, DEFAULT_TTL, DEFAULT_HEARTBEAT_INTERVAL);
    }

    GenerationTaskLeases(
            ReactiveRedisTokenLease leases,
            ReactiveStringRedisTemplate redisTemplate,
            Duration ttl,
            Duration heartbeatInterval) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.ttl = positive(ttl, "ttl");
        this.heartbeatInterval = positive(heartbeatInterval, "heartbeatInterval");
    }

    // 通过公共令牌租约组件获取任务锁。
    public Mono<TokenLease> acquire(UUID taskId) {
        return leases.acquire(GenerationTaskKeys.lock(taskId), ttl);
    }

    // 同时续租任务锁并刷新独立心跳键。
    public Mono<Boolean> heartbeat(TokenLease lease, String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        return leases
                .renew(lease, ttl)
                .flatMap(result -> result == LeaseResult.APPLIED
                        ? redisTemplate
                                .opsForValue()
                                .set(
                                        GenerationTaskKeys.heartbeat(taskId(lease)),
                                        workerId + "@" + Instant.now(),
                                        ttl)
                                .thenReturn(true)
                        : Mono.just(false));
    }

    // 在任务执行期间按固定间隔续租，令牌丢失时停止。
    public Flux<Boolean> heartbeats(TokenLease lease, String workerId) {
        return Flux.interval(heartbeatInterval)
                .concatMap(ignored -> heartbeat(lease, workerId))
                .takeWhile(Boolean::booleanValue);
    }

    // 释放锁并仅清除仍由当前令牌持有的心跳。
    public Mono<LeaseResult> release(TokenLease lease) {
        return leases.release(lease).flatMap(result -> result == LeaseResult.APPLIED
                ? redisTemplate
                        .delete(GenerationTaskKeys.heartbeat(taskId(lease)))
                        .thenReturn(result)
                : Mono.just(result));
    }

    public Mono<Boolean> alive(UUID taskId) {
        return redisTemplate.hasKey(GenerationTaskKeys.heartbeat(taskId));
    }

    private static UUID taskId(TokenLease lease) {
        var key = lease.key();
        var prefix = "pudans:ai:task-lock:";
        if (!key.startsWith(prefix)) {
            throw new IllegalArgumentException("unexpected generation task lease key");
        }
        return UUID.fromString(key.substring(prefix.length()));
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
