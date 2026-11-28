package cn.tolim.pudans.gateway.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfig {

    private static final String WILDCARD_ORIGIN = "*";

    @Bean
    public InitializingBean gatewayCorsPolicyValidator(GlobalCorsProperties globalCorsProperties) {
        return () -> validate(globalCorsProperties);
    }

    public static void validate(GlobalCorsProperties globalCorsProperties) {
        Map<String, CorsConfiguration> configurations = globalCorsProperties.getCorsConfigurations();
        if (configurations == null) {
            return;
        }
        configurations.forEach((path, configuration) -> {
            if (configuration == null) {
                return;
            }
            List<String> allowedOrigins = configuration.getAllowedOrigins();
            List<String> allowedOriginPatterns = configuration.getAllowedOriginPatterns();
            boolean wildcardOrigin = allowedOrigins != null && allowedOrigins.contains(WILDCARD_ORIGIN);
            boolean wildcardPattern = allowedOriginPatterns != null && allowedOriginPatterns.contains(WILDCARD_ORIGIN);
            if (Boolean.TRUE.equals(configuration.getAllowCredentials()) && (wildcardOrigin || wildcardPattern)) {
                throw new IllegalStateException(
                        "CORS configuration for path " + path + " allows credentials together with a wildcard origin");
            }
        });
    }
}