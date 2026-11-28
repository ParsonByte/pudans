package cn.tolim.pudans.iam.authorization.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
public class AuthorizationJacksonConfiguration {

    // Provides the Jackson 2 mapper used by authorization policy documents
    // Spring Boot 4 auto-configures a Jackson 3 mapper, the IAM foundation uses Jackson 2
    // Returns: ObjectMapper
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper authorizationObjectMapper() {
        return new ObjectMapper();
    }
}