package cn.tolim.pudans.security.authorization;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.TempGrants;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authentication.SwitchUserWebFilter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
class AuthorizationExchangeConfigurers {

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    // 公开端点直通，其余 API 交由规则判定
    @Bean
    @Order(0)
    SecurityConfigurer apiAuthorizationConfigurer(
            Roles roles, TempGrants tempGrants, AuditLog auditLog) {
        return http -> http.authorizeExchange(spec -> spec.pathMatchers(ApiGroups.SETUP_PATH)
                .permitAll()
                .pathMatchers(
                        ApiGroups.API_PREFIX + "/passkeys/assertion/**",
                        ApiGroups.API_PREFIX + "/oauth2/**",
                        ApiGroups.API_PREFIX + "/login/**",
                        ApiGroups.API_PREFIX + "/recovery/**",
                        ApiGroups.API_PREFIX + "/captcha/**",
                        ApiGroups.API_PREFIX + "/auth/**",
                        ApiGroups.API_PREFIX + "/avatars/**",
                        ApiGroups.API_PREFIX + "/site-assets/**",
                        "/avatars/**")
                .permitAll()
                .pathMatchers(
                        HttpMethod.GET,
                        ApiGroups.API_PREFIX + "/storagefiles/*/content",
                        ApiGroups.API_PREFIX + "/storagefiles/*/upload",
                        ApiGroups.API_PREFIX + "/storagefiles/*/upload/parts/*/url")
                .permitAll()
                .pathMatchers(
                        HttpMethod.PUT, ApiGroups.API_PREFIX + "/storagefiles/*/upload/parts/*")
                .permitAll()
                .pathMatchers(
                        HttpMethod.POST, ApiGroups.API_PREFIX + "/storagefiles/*/upload/complete")
                .permitAll()
                .pathMatchers(
                        HttpMethod.DELETE, ApiGroups.API_PREFIX + "/storagefiles/*/upload")
                .permitAll()
                .pathMatchers(ApiGroups.API_ROOT + "/**", "/actuator/**")
                .access(new RequestInfoAuthorizationManager(roles, tempGrants, auditLog)));
    }

    // 认证前路径按会话类型与角色限制访问
    @Bean
    @Order(200)
    SecurityConfigurer preAuthenticationAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> spec.pathMatchers("/sudo", "/sudo/**")
                .authenticated()
                .pathMatchers("/login/impersonate")
                // 令牌认证不能建立模拟会话，避免绕过 PAT scope。
                .access((authentication, context) -> authentication
                        .map(AuthorizationExchangeConfigurers::canImpersonate)
                        .<AuthorizationResult>map(AuthorizationDecision::new)
                        .switchIfEmpty(Mono.fromSupplier(() -> new AuthorizationDecision(false))))
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
                        .map(a -> !authenticationTrustResolver.isAnonymous(a)
                                && !(a instanceof JwtAuthenticationToken))
                        .<AuthorizationResult>map(AuthorizationDecision::new)
                        .switchIfEmpty(Mono.fromSupplier(() -> new AuthorizationDecision(false)))));
    }

    // 只有浏览器会话中的超级管理员可以模拟其他用户。
    private static boolean canImpersonate(Authentication authentication) {
        return !(authentication instanceof JwtAuthenticationToken)
                && AuthorityUtils.containsSuperRole(
                        AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()));
    }

    // 未显式声明的路径默认放行
    @Bean
    @Order
    SecurityConfigurer permitAllAuthorizationConfigurer() {
        return http -> http.authorizeExchange(spec -> spec.anyExchange().permitAll());
    }
}
