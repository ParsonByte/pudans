package cn.tolim.pudans.security.authentication.token;

import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.infra.setting.MasterKeyService;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSource;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
class TokenSecurityConfigurer implements SecurityConfigurer {

    private final CryptoService cryptoService;

    private final ReactiveExtensionClient client;

    private final RoleService roleService;

    private final MasterKeyService masterKeyService;

    @Override
    public void configure(ServerHttpSecurity http) {
        var jwtDecoder =
                withJwkSource(signedJWT -> Flux.just(cryptoService.getJwk())).build();
        var tokenAuthenticationManager =
                new TokenAuthenticationManager(client, jwtDecoder, roleService, masterKeyService);

        var entryPoint = new BearerTokenServerAuthenticationEntryPoint();
        var failureHandler = new ServerAuthenticationEntryPointFailureHandler(entryPoint);

        var filter = new AuthenticationWebFilter(tokenAuthenticationManager);
        filter.setServerAuthenticationConverter(new ServerBearerTokenAuthenticationConverter());
        filter.setAuthenticationFailureHandler(failureHandler);
        http.addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
