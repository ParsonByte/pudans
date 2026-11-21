package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AiGatewayRateLimiter {

    public static final String CODE_RATE_LIMITED = "ai-gateway-rate-limited";

    public static final String CODE_KEY_LIMITED = "ai-gateway-key-limited";

    public static final String CODE_USER_LIMITED = "ai-gateway-user-limited";

    private static final String KEY_PREFIX = "pudans:ai:gateway:";

    private static final long WINDOW_MILLIS = 60_000;

    private static final Duration REDIS_RETRY = Duration.ofSeconds(30);

    private static final RedisScript<String> RPM_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            if redis.call('ZCARD', key) >= limit then
                return '0'
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return '1'
            """, String.class);

    private static final RedisScript<Long> PERMIT_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local count = tonumber(redis.call('GET', key) or '0')
            if count >= limit then
                return 0
            end
            redis.call('INCR', key)
            redis.call('PEXPIRE', key, ttl)
            return 1
            """, Long.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local count = tonumber(redis.call('GET', key) or '0')
            if count <= 0 then
                return 0
            end
            return redis.call('DECR', key)
            """, Long.class);

    private final @Nullable ReactiveStringRedisTemplate redisTemplate;

    private final ConcurrentMap<String, LocalWindow> windows = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, AtomicInteger> permits = new ConcurrentHashMap<>();

    private final AtomicLong redisRetryAt = new AtomicLong();

    private Clock clock = Clock.systemUTC();

    public AiGatewayRateLimiter(@Nullable ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<Permit> acquire(AiProvider provider, AiProviderAccount account) {
        var providerName = nameOf(provider);
        var accountName = nameOf(account);
        var acquired = new ArrayList<Slot>();
        return requireRpm("provider", providerName, maxRpm(provider))
                .then(requireRpm("account", accountName, maxRpm(account)))
                .then(Mono.defer(() -> acquirePermit("provider", providerName, maxConcurrency(provider))))
                .doOnNext(acquired::add)
                .then(Mono.defer(() -> acquirePermit("account", accountName, maxConcurrency(account))))
                .doOnNext(acquired::add)
                // 许可列表必须在订阅时快照，避免在装配阶段生成空列表
                .then(Mono.fromSupplier(() -> new Permit(List.copyOf(acquired))))
                .onErrorResume(error -> releaseAll(acquired).then(Mono.error(error)));
    }

    // 调用密钥并发许可与供应商许可使用同一套本地/Redis 计数
    public Mono<Permit> acquireKey(@Nullable String callerKey, @Nullable Integer maxConcurrency) {
        if (callerKey == null || callerKey.isBlank() || !positive(maxConcurrency)) {
            return Mono.just(Permit.empty());
        }
        return acquireKeyPermit("key", callerKey, maxConcurrency)
                .map(slot -> new Permit(List.of(slot)))
                .defaultIfEmpty(Permit.empty());
    }

    public Mono<Permit> acquireUser(String userId, int maxConcurrency) {
        if (userId == null || userId.isBlank() || !positive(maxConcurrency)) {
            return Mono.just(Permit.empty());
        }
        var key = key("user", userId, "concurrency");
        return acquireSlot(key, maxConcurrency, true).flatMap(acquired -> acquired.allowed()
                ? Mono.just(new Permit(List.of(new Slot(key, acquired.local()))))
                : Mono.error(userLimited(userId)));
    }

    // 用户速率使用滑动窗口计数，和并发许可共用同一套本地/Redis 降级逻辑
    public Mono<Void> acquireUserRpm(String userId, int maxRpm) {
        if (userId == null || userId.isBlank() || !positive(maxRpm)) {
            return Mono.empty();
        }
        var key = key("user", userId, "rpm");
        return acquireSlot(key, maxRpm, false).flatMap(acquired -> acquired.allowed()
                ? Mono.empty()
                : Mono.error(userLimited(userId)));
    }

    public Mono<Permit> acquireUser(String userId, int maxConcurrency, int maxRpm) {
        if (userId == null || userId.isBlank()) {
            return Mono.just(Permit.empty());
        }
        return acquireUserRpm(userId, maxRpm).then(acquireUser(userId, maxConcurrency));
    }

    public Mono<Void> release(Permit permit) {
        if (permit == null || permit.slots().isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(permit.slots())
                .concatMap(slot -> slot.local()
                        ? Mono.fromRunnable(() -> releaseLocal(slot.key()))
                        : releaseRedis(slot.key()))
                .then();
    }

    private Mono<Void> requireRpm(String scope, @Nullable String name, @Nullable Integer limit) {
        if (name == null || !positive(limit)) {
            return Mono.empty();
        }
        var key = key(scope, name, "rpm");
        return acquireSlot(key, limit, false)
                .flatMap(acquired -> acquired.allowed()
                        ? Mono.empty()
                        : Mono.error(rateLimited(scope, name)));
    }

    private Mono<Slot> acquirePermit(String scope, @Nullable String name, @Nullable Integer limit) {
        if (name == null || !positive(limit)) {
            return Mono.empty();
        }
        var key = key(scope, name, "concurrency");
        return acquireSlot(key, limit, true).flatMap(acquired -> acquired.allowed()
                ? Mono.just(new Slot(key, acquired.local()))
                : Mono.error(rateLimited(scope, name)));
    }

    private Mono<Slot> acquireKeyPermit(String scope, String name, int limit) {
        var key = key(scope, name, "concurrency");
        return acquireSlot(key, limit, true).flatMap(acquired -> acquired.allowed()
                ? Mono.just(new Slot(key, acquired.local()))
                : Mono.error(keyLimited(name)));
    }

    private Mono<Acquisition> acquireSlot(String key, int limit, boolean concurrency) {
        if (redisTemplate == null || clock.millis() < redisRetryAt.get()) {
            return Mono.fromSupplier(() -> new Acquisition(localAcquire(key, limit, concurrency), true));
        }
        return (concurrency ? acquireRedisPermit(key, limit) : acquireRedisRpm(key, limit))
                .map(allowed -> new Acquisition(allowed, false))
                .onErrorResume(error -> {
                    degrade(error);
                    return Mono.just(new Acquisition(localAcquire(key, limit, concurrency), true));
                });
    }

    private Mono<Boolean> acquireRedisRpm(String key, int limit) {
        var args = List.of(
                String.valueOf(clock.millis()),
                String.valueOf(WINDOW_MILLIS),
                String.valueOf(limit),
                UUID.randomUUID().toString());
        return redisTemplate.execute(RPM_SCRIPT, List.of(key), args).next().map("1"::equals);
    }

    private Mono<Boolean> acquireRedisPermit(String key, int limit) {
        var args = List.of(String.valueOf(limit), String.valueOf(WINDOW_MILLIS));
        return redisTemplate.execute(PERMIT_SCRIPT, List.of(key), args).next().map(value -> value != null && value > 0);
    }

    private Mono<Void> releaseRedis(String key) {
        return redisTemplate.execute(RELEASE_SCRIPT, List.of(key), List.of()).next()
                .onErrorResume(error -> {
                    degrade(error);
                    releaseLocal(key);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> releaseAll(List<Slot> slots) {
        return release(new Permit(slots));
    }

    private boolean localAcquire(String key, int limit, boolean concurrency) {
        if (concurrency) {
            var counter = permits.computeIfAbsent(key, ignored -> new AtomicInteger());
            var current = counter.incrementAndGet();
            if (current > limit) {
                counter.decrementAndGet();
                return false;
            }
            return true;
        }
        var window = windows.computeIfAbsent(key, ignored -> new LocalWindow());
        synchronized (window) {
            var now = clock.millis();
            while (!window.timestamps.isEmpty() && window.timestamps.peekFirst() <= now - WINDOW_MILLIS) {
                window.timestamps.removeFirst();
            }
            if (window.timestamps.size() >= limit) {
                return false;
            }
            window.timestamps.addLast(now);
            return true;
        }
    }

    private void releaseLocal(String key) {
        var counter = permits.get(key);
        if (counter != null) {
            counter.updateAndGet(current -> current > 0 ? current - 1 : 0);
        }
    }

    private void degrade(Throwable error) {
        redisRetryAt.set(clock.millis() + REDIS_RETRY.toMillis());
        log.warn("AI 网关限流 Redis 不可用，暂时使用进程内计数。", error);
    }

    private static ApiException rateLimited(String scope, String name) {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                CODE_RATE_LIMITED,
                "AI 网关限流： " + scope + " " + name + " 超过速率或并发限制。");
    }

    private static ApiException userLimited(String userId) {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                CODE_USER_LIMITED,
                "用户请求超过速率或并发限制：" + userId + "。");
    }

    private static ApiException keyLimited(String name) {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                CODE_KEY_LIMITED,
                "AI 网关调用密钥 " + name + " 超过并发限制。");
    }

    private static String key(String scope, String name, String kind) {
        return KEY_PREFIX + scope + ':' + name + ':' + kind;
    }

    private static boolean positive(@Nullable Integer value) {
        return value != null && value > 0;
    }

    private static @Nullable String nameOf(@Nullable AiProvider provider) {
        return provider == null || provider.getMetadata() == null ? null : provider.getMetadata().getName();
    }

    private static @Nullable String nameOf(@Nullable AiProviderAccount account) {
        return account == null || account.getMetadata() == null ? null : account.getMetadata().getName();
    }

    private static @Nullable Integer maxRpm(@Nullable AiProvider provider) {
        return provider == null || provider.getSpec() == null ? null : provider.getSpec().getMaxRPM();
    }

    private static @Nullable Integer maxConcurrency(@Nullable AiProvider provider) {
        return provider == null || provider.getSpec() == null ? null : provider.getSpec().getMaxConcurrency();
    }

    private static @Nullable Integer maxRpm(@Nullable AiProviderAccount account) {
        return account == null || account.getSpec() == null ? null : account.getSpec().getMaxRPM();
    }

    private static @Nullable Integer maxConcurrency(@Nullable AiProviderAccount account) {
        return account == null || account.getSpec() == null ? null : account.getSpec().getMaxConcurrency();
    }

    private record Acquisition(boolean allowed, boolean local) {}

    private record LocalWindow(ArrayDeque<Long> timestamps) {

        private LocalWindow() {
            this(new ArrayDeque<>());
        }
    }

    public record Slot(String key, boolean local) {}

    public record Permit(List<Slot> slots) {

        public Permit {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }

        public static Permit empty() {
            return new Permit(List.of());
        }
    }
}