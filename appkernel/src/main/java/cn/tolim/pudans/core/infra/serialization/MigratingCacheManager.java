package cn.tolim.pudans.core.infra.serialization;

import java.util.Collection;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

public class MigratingCacheManager extends AbstractCacheManager {

    private final RedisCacheWriter writer;

    private final RedisCacheConfiguration configuration;

    private final FallbackJsonRedisSerializer serializer;

    private final CacheMigrator migrator;

    public MigratingCacheManager(
            RedisCacheWriter writer,
            RedisCacheConfiguration configuration,
            FallbackJsonRedisSerializer serializer,
            CacheMigrator migrator) {
        this.writer = writer;
        this.configuration = configuration;
        this.serializer = serializer;
        this.migrator = migrator;
    }

    @Override
    protected Collection<? extends Cache> loadCaches() {
        return List.of();
    }

    @Override
    protected Cache getMissingCache(String name) {
        return new MigratingCache(name, writer, configuration, serializer, migrator);
    }
}
