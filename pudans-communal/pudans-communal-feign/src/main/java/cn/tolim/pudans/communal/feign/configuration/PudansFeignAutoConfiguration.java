package cn.tolim.pudans.communal.feign.configuration;

import cn.tolim.pudans.communal.feign.PudansErrorDecoder;
import cn.tolim.pudans.communal.feign.TracePropagationInterceptor;
import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// Global conventions for every Feign client; no retries by default to keep POST calls idempotent.
@AutoConfiguration(afterName = "org.springframework.cloud.openfeign.FeignAutoConfiguration")
@ConditionalOnClass({Feign.class, RequestInterceptor.class})
@ConditionalOnProperty(prefix = "pudans.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansFeignProperties.class)
public class PudansFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestInterceptor pudansTracePropagationInterceptor(PudansFeignProperties properties) {
        return new TracePropagationInterceptor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder pudansFeignErrorDecoder(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                PudansFeignProperties properties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        return new PudansErrorDecoder(objectMapper, properties.isIncludeResponseBody());
    }

    @Bean
    @ConditionalOnMissingBean
    public Retryer pudansFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pudans.feign", name = "override-timeouts", havingValue = "true")
    public Request.Options pudansFeignRequestOptions(PudansFeignProperties properties) {
        return new Request.Options(properties.getConnectTimeout(), properties.getReadTimeout(),
                properties.isFollowRedirects());
    }
}