package cn.tolim.pudans.communal.redis.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public interface DistributedLock {

    Duration DEFAULT_WAIT_TIME = Duration.ofSeconds(3);
    Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

    Optional<LockHandle> tryAcquire(String name, Duration waitTime, Duration leaseTime);

    default Optional<LockHandle> tryAcquire(String name) {
        return tryAcquire(name, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
    }

    default <T> T executeWithLock(String name, Supplier<T> action) {
        return executeWithLock(name, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, action);
    }

    default <T> T executeWithLock(String name, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        try (LockHandle handle = tryAcquire(name, waitTime, leaseTime)
                .orElseThrow(() -> new LockAcquisitionException(name))) {
            return action.get();
        }
    }
}