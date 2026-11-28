package cn.tolim.pudans.iam.core.auth.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

// Redis backed accelerator for global login session lookups, the database stays the source of truth
public class RedisSessionCache implements SessionCache {

    public static final String KEY_PREFIX = "pudans:iam:sid:";

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSessionCache.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public RedisSessionCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public void put(String sid, Duration ttl) {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            template.opsForValue().set(key(sid), "active", ttl);
        } catch (RuntimeException exception) {
            LOGGER.debug("Cache global login session [{}] failed: {}", sid, exception.getMessage());
        }
    }

    @Override
    public void evict(String sid) {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.delete(key(sid));
        } catch (RuntimeException exception) {
            LOGGER.debug("Evict global login session [{}] failed: {}", sid, exception.getMessage());
        }
    }

    @Override
    public boolean contains(String sid) {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(template.hasKey(key(sid)));
        } catch (RuntimeException exception) {
            LOGGER.debug("Check global login session [{}] failed: {}", sid, exception.getMessage());
            return false;
        }
    }

    private String key(String sid) {
        return KEY_PREFIX + sid;
    }
}