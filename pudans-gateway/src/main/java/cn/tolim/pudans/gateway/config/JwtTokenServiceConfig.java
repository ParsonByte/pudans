package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.communal.core.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JwtTokenServiceConfig {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${pudans.security.jwt.secret}") String secret,
            @Value("${pudans.security.jwt.issuer}") String issuer,
            @Value("${pudans.security.jwt.audience}") String audience,
            @Value("${pudans.security.jwt.ttl-seconds:3600}") long ttlSeconds) {
        return new JwtTokenService(secret, issuer, audience, ttlSeconds);
    }
}