package cn.tolim.pudans.communal.redis.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pudans.redis")
public class PudansRedisProperties {

    private boolean enabled = true;

    private String keyPrefix = "pudans";

    private Duration defaultTtl = Duration.ofMinutes(30);

    private boolean cacheEnabled = true;

    private final Lock lock = new Lock();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        if (keyPrefix.contains(":")) {
            throw new IllegalArgumentException("keyPrefix must not contain ':'");
        }
        this.keyPrefix = keyPrefix;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        if (defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()) {
            throw new IllegalArgumentException("defaultTtl must be positive");
        }
        this.defaultTtl = defaultTtl;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Lock getLock() {
        return lock;
    }

    public static class Lock {

        private Duration waitTime = Duration.ofSeconds(3);

        private Duration leaseTime = Duration.ofSeconds(30);

        public Duration getWaitTime() {
            return waitTime;
        }

        public void setWaitTime(Duration waitTime) {
            if (waitTime == null || waitTime.isNegative()) {
                throw new IllegalArgumentException("lock.waitTime must not be negative");
            }
            this.waitTime = waitTime;
        }

        public Duration getLeaseTime() {
            return leaseTime;
        }

        public void setLeaseTime(Duration leaseTime) {
            if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
                throw new IllegalArgumentException("lock.leaseTime must be positive");
            }
            this.leaseTime = leaseTime;
        }
    }
}