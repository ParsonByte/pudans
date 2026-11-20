package cn.tolim.pudans.core.ai.routing.circuit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveSetOperations;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 本地熔断状态定期镜像到 Redis，让其他副本也能感知冷却中的供应商
@Slf4j
public final class RedisCircuitBreaker extends InMemoryCircuitBreaker implements SmartLifecycle {

    private static final String KEY_PREFIX = "ai:circuit:";

    private static final String KEY_INDEX = "ai:circuit:keys";

    private static final String FIELD_STATE = "state";

    private static final String FIELD_RETRY_AT = "retryAt";

    private static final Duration SYNC_INTERVAL = Duration.ofSeconds(10);

    private final ReactiveStringRedisTemplate redisTemplate;

    private volatile boolean running;

    private Disposable sync;

    public RedisCircuitBreaker(CircuitBreakerConfig config, ReactiveStringRedisTemplate redisTemplate) {
        super(config);
        this.redisTemplate = redisTemplate;
    }

    // 每个周期先发布本地已打开的熔断，再吸收其他副本的打开状态
    public Mono<Long> syncOnce() {
        return publishLocal().then(pullRemote());
    }

    private Mono<Void> publishLocal() {
        return Flux.fromIterable(keys()).concatMap(this::publish).then();
    }

    private Mono<Void> publish(String key) {
        var retryAt = retryAt(key);
        if (retryAt == null) {
            return Mono.empty();
        }
        var redisKey = redisKey(key);
        var fields = Map.of(FIELD_STATE, CircuitState.OPEN.name(), FIELD_RETRY_AT, retryAt.toString());
        var ttl = Duration.between(Instant.now(), retryAt).plus(Duration.ofMinutes(1));
        if (ttl.isNegative()) {
            ttl = Duration.ofMinutes(1);
        }
        var effectiveTtl = ttl;
        ReactiveSetOperations<String, String> setOps = redisTemplate.opsForSet();
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        return setOps
                .add(KEY_INDEX, key)
                .then(hashOps.putAll(redisKey, fields))
                .then(redisTemplate.expire(redisKey, effectiveTtl))
                .then()
                .onErrorResume(error -> {
                    log.debug("发布熔断状态 {} 失败：{}", key, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Long> pullRemote() {
        ReactiveSetOperations<String, String> setOps = redisTemplate.opsForSet();
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        return setOps
                .members(KEY_INDEX)
                .concatMap(key -> hashOps
                        .entries(redisKey(key))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .flatMap(fields -> adopt(key, fields)))
                .onErrorResume(error -> {
                    log.debug("读取远端熔断状态失败：{}", error.getMessage());
                    return Flux.empty();
                })
                .count();
    }

    private Mono<Long> adopt(String key, Map<String, String> fields) {
        if (!CircuitState.OPEN.name().equals(fields.get(FIELD_STATE))) {
            return Mono.just(0L);
        }
        var retryAt = parseInstant(fields.get(FIELD_RETRY_AT));
        if (retryAt == null || !retryAt.isAfter(Instant.now())) {
            return Mono.just(0L);
        }
        forceOpen(key, retryAt);
        return Mono.just(1L);
    }

    private static @Nullable Instant parseInstant(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String redisKey(String key) {
        return KEY_PREFIX + key;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        sync = Flux.interval(SYNC_INTERVAL)
                .concatMap(tick -> syncOnce().onErrorResume(error -> {
                    log.debug("熔断状态同步失败。", error);
                    return Mono.empty();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("熔断状态同步调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (sync != null) {
            sync.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}