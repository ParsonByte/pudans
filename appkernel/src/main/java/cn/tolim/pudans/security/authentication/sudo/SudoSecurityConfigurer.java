package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.security.authentication.SecurityConfigurer;
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

    // 在匿名认证之后注册安全确认过滤器
    @Override
    public void configure(ServerHttpSecurity http) {
        http.addFilterAfter(
                new SudoModeWebFilter(sudoChallenges, responseContext), SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION);
    }
}
