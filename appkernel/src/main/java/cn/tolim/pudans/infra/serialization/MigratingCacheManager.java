package cn.tolim.pudans.infra.serialization;

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

    // 保存创建缓存所需的共享配置。
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

    // 缓存按需创建，启动时不预加载。
    @Override
    protected Collection<? extends Cache> loadCaches() {
        return List.of();
    }

    // 缺失缓存使用迁移实现创建。
    @Override
    protected Cache getMissingCache(String name) {
        return new MigratingCache(name, writer, configuration, serializer, migrator);
    }
}
