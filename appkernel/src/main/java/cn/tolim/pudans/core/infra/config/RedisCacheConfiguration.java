package cn.tolim.pudans.core.infra.config;

import cn.tolim.pudans.core.infra.serialization.CacheMigrator;
import cn.tolim.pudans.core.infra.serialization.JacksonSerialization;
import cn.tolim.pudans.core.infra.serialization.MigratingCacheManager;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
class RedisCacheConfiguration {

    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, CacheMigrator migrator) {
        var serializer = JacksonSerialization.cacheSerializer();
        var configuration = org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return new MigratingCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory), configuration, serializer, migrator);
    }
}
