package cn.tolim.pudans.communal.redis.lock;

import cn.tolim.pudans.communal.redis.key.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Fallback lock using SET NX PX and a compare-and-delete Lua script.
public class RedisTemplateDistributedLock implements DistributedLock {

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final long RETRY_INTERVAL_MILLIS = 50L;

    private final StringRedisTemplate redisTemplate;
    private final RedisKeys keys;

    public RedisTemplateDistributedLock(StringRedisTemplate redisTemplate, RedisKeys keys) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate must not be null");
        }
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        this.redisTemplate = redisTemplate;
        this.keys = keys;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String name, Duration waitTime, Duration leaseTime) {
        requireName(name);
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("waitTime must not be negative");
        }
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }
        String key = keys.lock(name);
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + waitTime.toNanos();
        while (true) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime);
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(new RedisTemplateLockHandle(redisTemplate, key, token, name));
            }
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LockAcquisitionException(name, "interrupted while acquiring lock: " + name, exception);
            }
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("lock name must not be blank");
        }
    }

    private static final class RedisTemplateLockHandle implements LockHandle {

        private final StringRedisTemplate redisTemplate;
        private final String key;
        private final String token;
        private final String name;

        private RedisTemplateLockHandle(StringRedisTemplate redisTemplate, String key, String token, String name) {
            this.redisTemplate = redisTemplate;
            this.key = key;
            this.token = token;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void close() {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        }
    }
}