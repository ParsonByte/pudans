package cn.tolim.pudans.search.config;

import cn.tolim.pudans.communal.security.RestAuthenticationEntryPoint;
import cn.tolim.pudans.search.security.InternalTokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

// Internal-only security: actuator probes stay public, every other call needs the gateway token
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SearchSecurityConfiguration {

    @Bean
    public SecurityFilterChain searchSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${pudans.internal.token}") String internalToken) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        InternalTokenAuthenticationFilter tokenFilter =
                new InternalTokenAuthenticationFilter(internalToken, objectMapper);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/prometheus", "/actuator/prometheus/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

}