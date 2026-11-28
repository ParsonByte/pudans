package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.security.GlobalSessionValidator;
import cn.tolim.pudans.gateway.security.IamSessionValidationClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public GlobalSessionValidator globalSessionValidator(GatewayProperties properties,
                                                         ObjectMapper objectMapper,
                                                         ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                                         ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerFilterProvider) {
        GatewayProperties.Auth auth = properties.getAuth();
        if (!auth.isSessionEnabled()) {
            return GlobalSessionValidator.disabled();
        }
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        // lb:// session validation resolves the IAM instance through Spring Cloud LoadBalancer
        if (auth.getSessionValidateUrl().startsWith("lb://")) {
            ReactorLoadBalancerExchangeFilterFunction filter = loadBalancerFilterProvider.getIfAvailable();
            if (filter == null) {
                throw new IllegalStateException(
                        "lb:// session validation requires Spring Cloud LoadBalancer");
            }
            webClientBuilder = webClientBuilder.clone().filter(filter);
        }
        return new IamSessionValidationClient(
                webClientBuilder,
                objectMapper,
                auth.getSessionValidateUrl(),
                auth.getSessionCacheTtlSeconds(),
                auth.getSessionTimeoutMillis());
    }
}