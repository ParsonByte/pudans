package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
@Order(0)
class OAuth2SecurityConfigurer implements SecurityConfigurer {

    private final ServerSecurityContextRepository securityContextRepository;

    private final Connections connections;

    private final OAuth2AuthenticationSession authenticationSession;

    private final ServerResponse.Context context;

    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    private final SecurityProperties securityProperties;

    // 汇总第三方登录所需的仓库、连接与会话依赖。
    public OAuth2SecurityConfigurer(
            ServerSecurityContextRepository securityContextRepository,
            Connections connections,
            OAuth2AuthenticationSession authenticationSession,
            ServerResponse.Context context,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            SecurityProperties securityProperties) {
        this.securityContextRepository = securityContextRepository;
        this.connections = connections;
        this.authenticationSession = authenticationSession;
        this.context = context;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.securityProperties = securityProperties;
    }

    // 注册映射过滤器与双因素拦截，再启用第三方登录。
    @Override
    public void configure(ServerHttpSecurity http) {
        var mapOAuth2Filter = new MapOAuth2AuthenticationFilter(
                securityContextRepository, connections, authenticationSession, context);
        http.addFilterBefore(mapOAuth2Filter, SecurityWebFiltersOrder.AUTHENTICATION);
        http.addFilterBefore(
                new TwoFactorOAuth2GateFilter(securityContextRepository, context),
                SecurityWebFiltersOrder.HTTP_BASIC);
        http.oauth2Login(oauth2 -> oauth2
                .clientRegistrationRepository(clientRegistrationRepository)
                .authorizedClientRepository(new WebSessionServerOAuth2AuthorizedClientRepository())
                .loginPage("/login")
                .authenticationSuccessHandler(new OAuth2LoginSuccessHandler(
                        connections, authenticationSession, securityProperties, context)));
    }
}
