package cn.tolim.pudans.communal.log;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "pudans.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansLogProperties.class)
public class PudansLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter pudansTraceIdFilter(PudansLogProperties properties, ObjectProvider<Tracer> tracerProvider) {
        return new TraceIdFilter(properties, tracerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pudans.log", name = "request-logging", havingValue = "true", matchIfMissing = true)
    public RequestLoggingFilter pudansRequestLoggingFilter(PudansLogProperties properties) {
        return new RequestLoggingFilter(properties);
    }
}