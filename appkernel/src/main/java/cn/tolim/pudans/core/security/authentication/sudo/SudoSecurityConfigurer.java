package cn.tolim.pudans.core.security.authentication.sudo;

import cn.tolim.pudans.core.security.authentication.SecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
@RequiredArgsConstructor
class SudoSecurityConfigurer implements SecurityConfigurer {

    private final SudoChallenges sudoChallenges;

    private final ServerResponse.Context responseContext;

    @Override
    public void configure(ServerHttpSecurity http) {
        http.addFilterAfter(
                new SudoModeWebFilter(sudoChallenges, responseContext), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
    }
}
