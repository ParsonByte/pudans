package cn.tolim.pudans.iam.core.auth;

import cn.tolim.pudans.iam.core.auth.session.RedisSessionCache;
import cn.tolim.pudans.iam.core.auth.session.SessionCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IamAuthProperties.class)
public class IamAuthConfiguration {

    // Redis backed session cache, falls back to database only lookups when Redis is unavailable
    // Parameter redisTemplateProvider: ObjectProvider
    // Returns: SessionCache
    @Bean
    @ConditionalOnMissingBean
    public SessionCache sessionCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new RedisSessionCache(redisTemplateProvider);
    }
}