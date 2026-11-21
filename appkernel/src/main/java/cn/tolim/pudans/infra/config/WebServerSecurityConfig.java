package cn.tolim.pudans.infra.config;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.infra.api.AnonymousUserConst;
import cn.tolim.pudans.security.api.authentication.LoginCipher;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.audit.AuthenticatedUserWebFilter;
import cn.tolim.pudans.security.authentication.UserDetailsLoader;
import cn.tolim.pudans.security.authentication.PudansServerRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.impl.RsaLoginCipher;
import cn.tolim.pudans.security.authentication.login.PublicKeyRouteBuilder;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
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
        var staticResourcesMatcher = pathMatchers(HttpMethod.GET, "/webjars/**");

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
    UserDetailsLoader userDetailsService(
            Accounts accounts,
            Groups groups,
            PudansProperties pudansProperties,
            SecurityParameters securitySettings) {
        var userDetailService = new UserDetailsLoader(accounts, groups);
        userDetailService.setTwoFactorAuthEnabled(() ->
                !pudansProperties.getSecurity().getTwoFactorAuth().isDisabled()
                        && securitySettings.isTotpEnabled());
        return userDetailService;
    }

    @Bean
    org.springframework.web.reactive.function.server.RouterFunction<
            org.springframework.web.reactive.function.server.ServerResponse>
    publicKeyRouter(LoginCipher loginCipher) {
        return new PublicKeyRouteBuilder(loginCipher).build();
    }

    @Bean
    LoginCipher loginCipher(PudansProperties pudansProperties) {
        return new RsaLoginCipher(pudansProperties.getWorkDir().resolve("keys"));
    }
}
