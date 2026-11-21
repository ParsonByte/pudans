package cn.tolim.pudans.security.authentication.switchuser;

import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.SwitchUserWebFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
class SwitchUserConfigurer implements SecurityConfigurer {

    private static final String IMPERSONATION_FAILED = "impersonation-failed";

    private final ReactiveUserDetailsService reactiveUserDetailsService;

    private final ServerResponse.Context context;

    // 注入用户详情服务与响应上下文
    SwitchUserConfigurer(ReactiveUserDetailsService reactiveUserDetailsService, ServerResponse.Context context) {
        this.reactiveUserDetailsService = reactiveUserDetailsService;
        this.context = context;
    }

    // 注册用户模拟过滤器并配置成功与失败响应
    @Override
    public void configure(ServerHttpSecurity http) {
        ServerAuthenticationSuccessHandler successHandler = (exchange, authentication) -> {
            var response = exchange.getExchange().getResponse();
            response.setStatusCode(HttpStatus.NO_CONTENT);
            return response.setComplete();
        };
        ServerAuthenticationFailureHandler failureHandler = (exchange, exception) -> ProblemDetailResponses.write(
                exchange.getExchange(),
                context,
                HttpStatus.UNAUTHORIZED,
                IMPERSONATION_FAILED,
                "用户模拟失败。");
        var filter = new SwitchUserWebFilter(reactiveUserDetailsService, successHandler, failureHandler);
        http.addFilterAfter(filter, SecurityWebFiltersOrder.AUTHORIZATION);
    }
}
