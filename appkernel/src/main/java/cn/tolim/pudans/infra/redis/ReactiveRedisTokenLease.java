package cn.tolim.pudans.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveRedisTokenLease {

    private static final RedisScript<Long> RENEW_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public ReactiveRedisTokenLease(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    public Mono<TokenLease> acquire(String key, Duration ttl) {
        requireKey(key);
        requireTtl(ttl);
        return Mono.defer(() -> {
            var token = UUID.randomUUID().toString();
            return redisTemplate
                    .opsForValue()
                    .setIfAbsent(key, token, ttl)
                    .defaultIfEmpty(false)
                    .flatMap(acquired -> Boolean.TRUE.equals(acquired)
                            ? Mono.just(new TokenLease(key, token, ttl))
                            : Mono.empty());
        });
    }

    public Mono<LeaseResult> renew(TokenLease lease, Duration ttl) {
        Objects.requireNonNull(lease, "lease");
        requireTtl(ttl);
        return apply(RENEW_SCRIPT, lease, List.of(lease.token(), String.valueOf(ttl.toMillis())));
    }

    public Mono<LeaseResult> release(TokenLease lease) {
        Objects.requireNonNull(lease, "lease");
        return apply(RELEASE_SCRIPT, lease, List.of(lease.token()));
    }

    private Mono<LeaseResult> apply(RedisScript<Long> script, TokenLease lease, List<Object> arguments) {
        return redisTemplate
                .execute(script, List.of(lease.key()), arguments)
                .next()
                .map(result -> result != null && result > 0 ? LeaseResult.APPLIED : LeaseResult.LOST)
                .defaultIfEmpty(LeaseResult.LOST);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public record TokenLease(String key, String token, Duration ttl) {

        public TokenLease {
            requireKey(key);
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token must not be blank");
            }
            requireTtl(ttl);
        }
    }

    public enum LeaseResult {
        APPLIED,
        LOST
    }
}
