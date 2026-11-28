package cn.tolim.pudans.communal.redis.configuration;

import cn.tolim.pudans.communal.redis.cache.PudansRedisCache;
import cn.tolim.pudans.communal.redis.key.RedisKeys;
import cn.tolim.pudans.communal.redis.lock.DistributedLock;
import cn.tolim.pudans.communal.redis.lock.RedissonDistributedLock;
import cn.tolim.pudans.communal.redis.lock.RedisTemplateDistributedLock;
import cn.tolim.pudans.communal.redis.serializer.RedisSerializers;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(afterName = {
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        "org.redisson.spring.starter.RedissonAutoConfigurationV4",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration"
})
@ConditionalOnClass({RedisConnectionFactory.class, RedisTemplate.class})
@ConditionalOnProperty(prefix = "pudans.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansRedisProperties.class)
public class PudansRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisKeys pudansRedisKeys(PudansRedisProperties properties) {
        return new RedisKeys(properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public PudansRedisCache pudansRedisCache(StringRedisTemplate redisTemplate,
                                             ObjectProvider<ObjectMapper> objectMapperProvider,
                                             PudansRedisProperties properties) {
        return new PudansRedisCache(redisTemplate, defaultObjectMapper(objectMapperProvider), properties);
    }

    // Redisson is the preferred lock provider; declared first so the fallback sees this bean definition.
    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    public DistributedLock pudansRedissonDistributedLock(RedissonClient redissonClient, RedisKeys keys) {
        return new RedissonDistributedLock(redissonClient, keys);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public DistributedLock pudansRedisTemplateDistributedLock(StringRedisTemplate redisTemplate, RedisKeys keys) {
        return new RedisTemplateDistributedLock(redisTemplate, keys);
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(prefix = "pudans.redis", name = "cache-enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheManager pudansRedisCacheManager(RedisConnectionFactory connectionFactory,
                                                     ObjectProvider<ObjectMapper> objectMapperProvider,
                                                     PudansRedisProperties properties) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getDefaultTtl())
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializers.utf8()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisSerializers.json(defaultObjectMapper(objectMapperProvider))));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    private ObjectMapper defaultObjectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
    }
}