package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.service.TempRoleService;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.SwitchUserWebFilter;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.MediaTypeServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
class AuthorizationExchangeConfigurers {

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    private static ServerWebExchangeMatcher createHtmlMatcher() {
        ServerWebExchangeMatcher get = ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/**");
        ServerWebExchangeMatcher notFavicon =
                new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.pathMatchers("/favicon.*"));
        MediaTypeServerWebExchangeMatcher html = new MediaTypeServerWebExchangeMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));
        return new AndServerWebExchangeMatcher(get, notFavicon, html);
    }

    @Bean
    @Order(0)
    SecurityConfigurer apiAuthorizationConfigurer(
            RoleService roleService, TempRoleService tempRoleService, AuditService auditService) {
        return http -> http.authorizeExchange(spec -> spec.pathMatchers(ApiGroups.SETUP_PATH)
                .permitAll()
                .pathMatchers(
                        ApiGroups.API_PREFIX + "/passkeys/assertion/**",
                        ApiGroups.API_PREFIX + "/oauth2/**",
                        ApiGroups.API_PREFIX + "/login/**",
                        ApiGroups.API_PREFIX + "/recovery/**")
                .permitAll()
                .pathMatchers(ApiGroups.API_ROOT + "/**", "/actuator/**")
                .access(new RequestInfoAuthorizationManager(roleService, tempRoleService, auditService)));
    }

    @Bean
    @Order(100)
    SecurityConfigurer unauthenticatedAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> {
            spec.pathMatchers(HttpMethod.GET, "/login")
                    .access((authentication, context) -> authentication
                            .map(a -> !authenticationTrustResolver.isAuthenticated(a))
                            .defaultIfEmpty(true)
                            .map(AuthorizationDecision::new));
        });
    }

    @Bean
    @Order(200)
    SecurityConfigurer preAuthenticationAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> spec.pathMatchers("/sudo", "/sudo/**")
                .authenticated()
                .pathMatchers("/login/impersonate")
                .hasRole(AuthorityUtils.SUPER_ROLE_NAME)
                .pathMatchers("/logout/impersonate")
                .hasAuthority(SwitchUserWebFilter.ROLE_PREVIOUS_ADMINISTRATOR)
                .pathMatchers("/challenges/**")
                .access((authentication, context) -> authentication
                        .map(TwoFactorAuthentication.class::isInstance)
                        .<AuthorizationResult>map(AuthorizationDecision::new)
                        .switchIfEmpty(Mono.fromSupplier(() -> new AuthorizationDecision(false))))
                .pathMatchers(HttpMethod.POST, "/login")
                .permitAll()
                .pathMatchers("/login/**")
                .permitAll()
                .pathMatchers("/logout")
                .access((authentication, context) -> authentication
                        .map(a -> !authenticationTrustResolver.isAnonymous(a))
                        .<AuthorizationResult>map(AuthorizationDecision::new)
                        .switchIfEmpty(Mono.fromSupplier(() -> new AuthorizationDecision(false)))));
    }

    @Bean
    @Order(300)
    SecurityConfigurer authenticatedAuthorizationConfigurer() {

        return http -> http.authorizeExchange(spec -> spec.pathMatchers("/complete-profile/**")
                .authenticated()
                .pathMatchers("/")
                .authenticated());
    }

    @Bean
    @Order(400)
    SecurityConfigurer anonymousOrAuthenticatedAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> spec.matchers(createHtmlMatcher())
                .access((authentication, context) ->


                        authentication
                                .map(Authentication::isAuthenticated)
                                .<AuthorizationResult>map(AuthorizationDecision::new)
                                .switchIfEmpty(Mono.fromSupplier(() -> new AuthorizationDecision(false)))));
    }

    @Bean
    @Order
    SecurityConfigurer permitAllAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> spec.anyExchange().permitAll());
    }
}
