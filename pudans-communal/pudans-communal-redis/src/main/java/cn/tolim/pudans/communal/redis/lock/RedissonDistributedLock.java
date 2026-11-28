package cn.tolim.pudans.communal.redis.lock;

import cn.tolim.pudans.communal.redis.key.RedisKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Redisson based lock with watchdog-free fixed lease time.
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redissonClient;
    private final RedisKeys keys;

    public RedissonDistributedLock(RedissonClient redissonClient, RedisKeys keys) {
        if (redissonClient == null) {
            throw new IllegalArgumentException("redissonClient must not be null");
        }
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        this.redissonClient = redissonClient;
        this.keys = keys;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String name, Duration waitTime, Duration leaseTime) {
        requireName(name);
        requireDuration(waitTime, "waitTime", true);
        requireDuration(leaseTime, "leaseTime", false);
        RLock lock = redissonClient.getLock(keys.lock(name));
        try {
            boolean acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            return acquired ? Optional.of(new RedissonLockHandle(lock, name)) : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(name, "interrupted while acquiring lock: " + name, exception);
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("lock name must not be blank");
        }
    }

    private void requireDuration(Duration duration, String field, boolean allowZero) {
        if (duration == null || duration.isNegative() || (!allowZero && duration.isZero())) {
            throw new IllegalArgumentException(field + (allowZero
                    ? " must not be negative"
                    : " must be positive"));
        }
    }

    private static final class RedissonLockHandle implements LockHandle {

        private final RLock lock;
        private final String name;

        private RedissonLockHandle(RLock lock, String name) {
            this.lock = lock;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void close() {
            if (!lock.isHeldByCurrentThread()) {
                return;
            }
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException exception) {
                throw new LockAcquisitionException(name, "lock was not held by the current thread: " + name, exception);
            }
        }
    }
}