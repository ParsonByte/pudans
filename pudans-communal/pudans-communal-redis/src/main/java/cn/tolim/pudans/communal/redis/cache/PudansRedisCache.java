package cn.tolim.pudans.communal.redis.cache;

import cn.tolim.pudans.communal.redis.configuration.PudansRedisProperties;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

// JSON cache on top of StringRedisTemplate; callers pass final keys built with RedisKeys.
public class PudansRedisCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PudansRedisProperties properties;

    public PudansRedisCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                            PudansRedisProperties properties) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<String> get(String key) {
        requireKey(key);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        requireKey(key);
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(payload, type));
        } catch (RuntimeException exception) {
            throw new RedisCacheException("failed to deserialize cache value for key " + key, exception);
        }
    }

    public void set(String key, Object value) {
        set(key, value, properties.getDefaultTtl());
    }

    public void set(String key, Object value, Duration ttl) {
        requireKey(key);
        requireValue(value);
        requireTtl(ttl);
        redisTemplate.opsForValue().set(key, serialize(key, value), ttl);
    }

    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        requireKey(key);
        requireValue(value);
        requireTtl(ttl);
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(key, serialize(key, value), ttl);
        return Boolean.TRUE.equals(stored);
    }

    public boolean delete(String key) {
        requireKey(key);
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean exists(String key) {
        requireKey(key);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public long increment(String key, long delta) {
        requireKey(key);
        Long value = redisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    public boolean expire(String key, Duration ttl) {
        requireKey(key);
        requireTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
    }

    public Optional<Duration> ttl(String key) {
        requireKey(key);
        Long seconds = redisTemplate.getExpire(key);
        if (seconds == null || seconds < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    // Loads and caches a value; the first writer wins to reduce stampedes.
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        requireTtl(ttl);
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T loaded = loader.get();
        if (loaded == null) {
            return null;
        }
        if (setIfAbsent(key, loaded, ttl)) {
            return loaded;
        }
        return get(key, type).orElse(loaded);
    }

    private String serialize(String key, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new RedisCacheException("failed to serialize cache value for key " + key, exception);
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    private void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
}