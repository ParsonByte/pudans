package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.service.UserConnectionService;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
@Order(0)
class OAuth2SecurityConfigurer implements SecurityConfigurer {

    private final ServerSecurityContextRepository securityContextRepository;

    private final UserConnectionService connectionService;

    private final OAuth2AuthenticationSession authenticationSession;

    private final OAuth2AuthenticationTokenCache authenticationCache;

    private final ServerResponse.Context context;

    public OAuth2SecurityConfigurer(
            ServerSecurityContextRepository securityContextRepository,
            UserConnectionService connectionService,
            OAuth2AuthenticationSession authenticationSession,
            OAuth2AuthenticationTokenCache authenticationCache,
            ServerResponse.Context context) {
        this.securityContextRepository = securityContextRepository;
        this.connectionService = connectionService;
        this.authenticationSession = authenticationSession;
        this.authenticationCache = authenticationCache;
        this.context = context;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var mapOAuth2Filter = new MapOAuth2AuthenticationFilter(
                securityContextRepository, connectionService, authenticationSession, authenticationCache, context);
        http.addFilterBefore(mapOAuth2Filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
