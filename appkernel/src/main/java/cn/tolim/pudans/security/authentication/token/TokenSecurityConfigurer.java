package cn.tolim.pudans.security.authentication.token;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.contract.authentication.LoginCipher;
import cn.tolim.pudans.infra.setting.MasterKeys;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSource;

@Component
@Order(0)
@RequiredArgsConstructor
class TokenSecurityConfigurer implements SecurityConfigurer {

    private final LoginCipher loginCipher;

    private final ReactiveExtensionClient client;

    private final Roles roles;

    private final UserRoles userRoles;

    private final MasterKeys masterKeys;

    // 注册基于签名令牌解码的认证过滤器。
    @Override
    public void configure(ServerHttpSecurity http) {
        var jwtDecoder =
                withJwkSource(signedJWT -> Flux.just(loginCipher.getJwk())).build();
        var tokenAuthenticationManager =
                new TokenAuthenticationManager(client, jwtDecoder, roles, userRoles, masterKeys);

        var entryPoint = new BearerTokenServerAuthenticationEntryPoint();
        var failureHandler = new ServerAuthenticationEntryPointFailureHandler(entryPoint);

        var filter = new AuthenticationWebFilter(tokenAuthenticationManager);
        filter.setServerAuthenticationConverter(new SudoBearerTokenAuthenticationConverter());
        filter.setAuthenticationFailureHandler(failureHandler);
        http.addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
