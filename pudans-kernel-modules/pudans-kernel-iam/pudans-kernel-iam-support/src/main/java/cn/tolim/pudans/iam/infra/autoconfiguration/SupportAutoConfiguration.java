package cn.tolim.pudans.iam.infra.autoconfiguration;

import cn.tolim.pudans.iam.infra.web.useragent.DefaultUserAgentParser;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupportProperties.class)
public class SupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().set(1,
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public UserAgentParser userAgentParser() {
        return new DefaultUserAgentParser();
    }
}
