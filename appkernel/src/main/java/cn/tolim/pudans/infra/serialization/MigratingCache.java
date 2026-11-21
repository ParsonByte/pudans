package cn.tolim.pudans.infra.serialization;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

public class MigratingCache extends RedisCache {

    private final FallbackJsonRedisSerializer serializer;

    private final CacheMigrator migrator;

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
