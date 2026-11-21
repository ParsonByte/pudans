package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.serialization.JacksonSerialization;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.session.autoconfigure.SessionProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.SaveMode;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.session.security.SpringSessionBackedReactiveSessionRegistry;


@Configuration
@EnableSpringWebSession
class SessionConfiguration {

    @Bean
    ReactiveRedisTemplate<String, Object> sessionRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        var serializer = JacksonSerialization.sessionSerializer();
        var context = RedisSerializationContext.<String, Object>newSerializationContext()
                .key(RedisSerializer.string())
                .value(serializer)
                .hashKey(RedisSerializer.string())
                .hashValue(serializer)
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @Bean
    ReactiveRedisIndexedSessionRepository reactiveRedisSessionStore(
            ReactiveRedisConnectionFactory connectionFactory,
            ReactiveStringRedisTemplate stringRedisTemplate,
            SessionProperties sessionProperties,
            ServerProperties serverProperties) {
        var repository =
                new ReactiveRedisIndexedSessionRepository(sessionRedisTemplate(connectionFactory), stringRedisTemplate);
        repository.setRedisKeyNamespace("pudans:session");
        var timeout = Optional.ofNullable(sessionProperties.getTimeout())
                .orElseGet(() -> serverProperties.getReactive().getSession().getTimeout());
        repository.setDefaultMaxInactiveInterval(Optional.ofNullable(timeout).orElse(Duration.ofMinutes(30)));
        repository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
        return repository;
    }

    @Bean
    SpringSessionBackedReactiveSessionRegistry<ReactiveRedisIndexedSessionRepository.RedisSession> reactiveSessionRegistry(
            ReactiveRedisIndexedSessionRepository sessionRepository) {
        return new SpringSessionBackedReactiveSessionRegistry<>(sessionRepository, sessionRepository);
    }
}
