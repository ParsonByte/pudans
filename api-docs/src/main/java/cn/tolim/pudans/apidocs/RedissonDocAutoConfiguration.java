package cn.tolim.pudans.apidocs;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

// Supplies an inert Redisson client for the doc profile so services injecting RedissonClient
// start without a Redis server. The doc profile excludes the real Redisson auto-configuration.
@AutoConfiguration(beforeName = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@Profile("doc")
@ConditionalOnClass(RedissonClient.class)
public class RedissonDocAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient pudansOpenApiDocRedissonClient() {
        return InertProxy.create(RedissonClient.class);
    }

}