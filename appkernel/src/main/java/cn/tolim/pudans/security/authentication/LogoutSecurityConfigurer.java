package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.core.user.UserLoginOrLogoutProcessing;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Order(0)
class LogoutSecurityConfigurer implements SecurityConfigurer {

    private final ApplicationContext applicationContext;

    private final UserLoginOrLogoutProcessing userLoginOrLogoutProcessing;

    private final ServerSecurityContextRepository securityContextRepository;

    // 注册登出处理器与登出成功后的响应处理。
    @Override
    public void configure(ServerHttpSecurity http) {
        http.logout(
                logout -> logout.logoutHandler(getLogoutHandler()).logoutSuccessHandler(new LogoutSuccessHandler()));
    }

    // 组合默认上下文清理与容器内自定义登出处理器。
    private ServerLogoutHandler getLogoutHandler() {
        var defaultLogoutHandler = new SecurityContextServerLogoutHandler();
        defaultLogoutHandler.setSecurityContextRepository(securityContextRepository);
        var logoutHandlers = new ArrayList<ServerLogoutHandler>();
        logoutHandlers.add(defaultLogoutHandler);
        applicationContext.getBeanProvider(ServerLogoutHandler.class).forEach(logoutHandlers::add);
        if (logoutHandlers.size() == 1) {
            return logoutHandlers.getFirst();
        }
        return new DelegatingServerLogoutHandler(logoutHandlers);
    }

    private class LogoutSuccessHandler implements ServerLogoutSuccessHandler {

        // 登出成功后执行登出业务处理并返回无内容状态。
        @Override
        public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
            return userLoginOrLogoutProcessing
                    .logoutProcessing(authentication.getName())
                    .then(Mono.defer(() -> {
                        var response = exchange.getExchange().getResponse();
                        response.setStatusCode(HttpStatus.NO_CONTENT);
                        return response.setComplete();
                    }));
        }
    }
}
