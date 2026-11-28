package cn.tolim.pudans.communal.redis.serializer;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

// Serializer factories for Spring Data Redis in a Spring Boot 4 (Jackson 3) runtime.
public final class RedisSerializers {

    private RedisSerializers() {
    }

    public static RedisSerializer<String> utf8() {
        return RedisSerializer.string();
    }

    public static RedisSerializer<Object> json(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }

    public static RedisTemplate<String, Object> jsonTemplate(RedisConnectionFactory connectionFactory,
                                                            ObjectMapper objectMapper) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("connectionFactory must not be null");
        }
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(utf8());
        template.setHashKeySerializer(utf8());
        RedisSerializer<Object> valueSerializer = json(objectMapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}