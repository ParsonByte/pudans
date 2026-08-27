package cn.tolim.pudans.infra.config;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.infra.AnonymousUserConst;
import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.security.audit.AuthenticatedUserWebFilter;
import cn.tolim.pudans.security.authentication.DefaultUserDetailService;
import cn.tolim.pudans.security.authentication.PudansServerRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.impl.RsaKeyService;
import cn.tolim.pudans.security.authentication.login.PublicKeyRouteBuilder;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;


@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class WebServerSecurityConfig {

    @Bean
    SecurityWebFilterChain filterChain(
            ServerHttpSecurity http,
            ObjectProvider<SecurityConfigurer> securityConfigurers,
            ServerSecurityContextRepository securityContextRepository,
            PudansProperties pudansProperties,
            ServerRequestCache serverRequestCache) {

        var pathMatcher = pathMatchers("/**");
        var staticResourcesMatcher = pathMatchers(
                HttpMethod.GET,
                "/ui-assets/**",
                "/plugins/{pluginName}/assets/**",
                "/webjars/**",
                "/js/**",
                "/styles/**",
                "/pudans-tracker.js",
                "/images/**");

        var securityMatcher = new AndServerWebExchangeMatcher(
                pathMatcher, new NegatedServerWebExchangeMatcher(staticResourcesMatcher));

        http.securityMatcher(securityMatcher)
                .anonymous(spec -> {
                    spec.authorities(AuthorityUtils.ROLE_PREFIX + AnonymousUserConst.Role);
                    spec.principal(AnonymousUserConst.PRINCIPAL);
                })
                .securityContextRepository(securityContextRepository)
                .httpBasic(basic -> {
                    if (pudansProperties.getSecurity().getBasicAuth().isDisabled()) {
                        basic.disable();
                    }
                })
                .headers(headerSpec -> headerSpec
                        .frameOptions(frameSpec -> {
                            var frameOptions = pudansProperties.getSecurity().getFrameOptions();
                            frameSpec.mode(frameOptions.getMode());
                            if (frameOptions.isDisabled()) {
                                frameSpec.disable();
                            }
                        })
                        .referrerPolicy(referrerPolicySpec -> referrerPolicySpec.policy(pudansProperties
                                .getSecurity()
                                .getReferrerOptions()
                                .getPolicy()))
                        .hsts(hstsSpec -> hstsSpec.includeSubdomains(false)))
                .requestCache(spec -> spec.requestCache(serverRequestCache))
                .addFilterAfter(new AuthenticatedUserWebFilter(),
                        org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHORIZATION);


        securityConfigurers.orderedStream().forEach(securityConfigurer -> securityConfigurer.configure(http));
        return http.build();
    }

    @Bean
    ServerRequestCache serverRequestCache() {
        return new PudansServerRequestCache();
    }

    @Bean
    ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    DefaultUserDetailService userDetailsService(
            UserService userService, UserGroupService userGroupService, PudansProperties pudansProperties) {
        var userDetailService = new DefaultUserDetailService(userService, userGroupService);
        var twoFactorAuthDisabled =
                pudansProperties.getSecurity().getTwoFactorAuth().isDisabled();
        userDetailService.setTwoFactorAuthDisabled(twoFactorAuthDisabled);
        return userDetailService;
    }

    @Bean
    @SuppressWarnings("deprecation")
    PasswordEncoder passwordEncoder() {


        var encodingId = "argon2@SpringSecurity_v5_8";
        var encoders = new HashMap<String, PasswordEncoder>();
        encoders.put(encodingId, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder(encodingId, encoders);
    }

    @Bean
    org.springframework.web.reactive.function.server.RouterFunction<
            org.springframework.web.reactive.function.server.ServerResponse>
    publicKeyRouter(CryptoService cryptoService) {
        return new PublicKeyRouteBuilder(cryptoService).build();
    }

    @Bean
    CryptoService cryptoService(PudansProperties pudansProperties) {
        return new RsaKeyService(pudansProperties.getWorkDir().resolve("keys"));
    }
}
