package cn.tolim.pudans.infra.serialization;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

public class MigratingCache extends RedisCache {

    private final FallbackJsonRedisSerializer serializer;

    private final CacheMigrator migrator;

    // 保存序列化器与迁移器以处理旧格式缓存项。
    MigratingCache(
            String name,
            RedisCacheWriter writer,
            RedisCacheConfiguration configuration,
            FallbackJsonRedisSerializer serializer,
            CacheMigrator migrator) {
        super(name, writer, configuration);
        this.serializer = serializer;
        this.migrator = migrator;
    }

    // 旧格式命中时调度迁移并返回解码值。
    @Override
    public ValueWrapper get(Object key) {
        var raw = getCacheWriter().get(getName(), serializeCacheKey(createCacheKey(key)));
        if (raw == null) {
            return null;
        }
        var decoded = serializer.decode(raw);
        if (decoded.legacy()) {
            migrator.schedule(this, key, decoded.value());
        }
        return toValueWrapper(decoded.value());
    }
}
