package cn.tolim.pudans.iam.infra.autoconfiguration;

import cn.tolim.pudans.iam.infra.jackjson.SupportJackson2Module;
import cn.tolim.pudans.iam.infra.redis.KeyStringRedisSerializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class SupportCacheConfiguration implements BeanClassLoaderAware {
    private final CacheProperties cacheProperties;
    private ClassLoader classLoader = getClass().getClassLoader();

    public SupportCacheConfiguration(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean
    @ConditionalOnMissingBean(name = "stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();
        KeyStringRedisSerializer keySerializer = keyStringRedisSerializer();
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(keySerializer);
        template.setStringSerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setDefaultSerializer(jsonSerializer);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();
        KeyStringRedisSerializer keySerializer = keyStringRedisSerializer();
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(keySerializer);
        template.setStringSerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisCacheManagerBuilderCustomizer.class)
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();
        String keyPrefix = cacheProperties.getRedis().getKeyPrefix();
        return builder -> builder.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .computePrefixWith(cacheName -> keyPrefix + ":" + cacheName + ":"));
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper());
    }

    private KeyStringRedisSerializer keyStringRedisSerializer() {
        return new KeyStringRedisSerializer(cacheProperties.getRedis().getKeyPrefix());
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        new Jackson2ObjectMapperBuilder().configure(objectMapper);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        objectMapper.registerModules(SupportJackson2Module.getModules(classLoader));
        return objectMapper;
    }
}
