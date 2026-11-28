package cn.tolim.pudans.communal.feign.configuration;

import cn.tolim.pudans.communal.feign.PudansErrorDecoder;
import cn.tolim.pudans.communal.feign.TracePropagationInterceptor;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// Explicit per-client configuration: @FeignClient(configuration = PudansFeignClientConfiguration.class).
@Configuration(proxyBeanMethods = false)
public class PudansFeignClientConfiguration {

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
    public Request.Options pudansFeignRequestOptions(PudansFeignProperties properties) {
        return new Request.Options(properties.getConnectTimeout(), properties.getReadTimeout(),
                properties.isFollowRedirects());
    }
}