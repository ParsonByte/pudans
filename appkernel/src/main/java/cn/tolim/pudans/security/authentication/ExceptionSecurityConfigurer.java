package cn.tolim.pudans.security.authentication;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.anyExchange;

import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthenticationEntryPoint;
import java.util.ArrayList;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.access.server.BearerTokenServerAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;
import org.springframework.security.web.server.authorization.ServerWebExchangeDelegatingServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
@Order(0)
public class ExceptionSecurityConfigurer implements SecurityConfigurer {

    private final ServerResponse.Context context;

    // 注入响应上下文，供各类异常处理器写出统一响应。
    public ExceptionSecurityConfigurer(ServerResponse.Context context) {
        this.context = context;
    }

    // 按请求类型分派认证入口与拒绝处理器，保证响应格式统一。
    @Override
    public void configure(ServerHttpSecurity http) {
        http.exceptionHandling(exception -> {
            var accessDeniedHandlers =
                    new ArrayList<ServerWebExchangeDelegatingServerAccessDeniedHandler.DelegateEntry>(2);
            accessDeniedHandlers.add(new ServerWebExchangeDelegatingServerAccessDeniedHandler.DelegateEntry(
                    new AuthenticationConverterServerWebExchangeMatcher(new ServerBearerTokenAuthenticationConverter()),
                    new BearerTokenServerAccessDeniedHandler()));
            accessDeniedHandlers.add(new ServerWebExchangeDelegatingServerAccessDeniedHandler.DelegateEntry(
                    anyExchange(), new ProblemDetailAccessDeniedHandler(context)));

            var entryPoints = new ArrayList<DelegatingServerAuthenticationEntryPoint.DelegateEntry>(2);
            entryPoints.add(new DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                    TwoFactorAuthenticationEntryPoint.MATCHER,
                    new TwoFactorAuthenticationEntryPoint(context)));
            entryPoints.add(new DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                    anyExchange(), new DefaultServerAuthenticationEntryPoint(context)));

            exception
                    .authenticationEntryPoint(new DelegatingServerAuthenticationEntryPoint(entryPoints))
                    .accessDeniedHandler(
                            new ServerWebExchangeDelegatingServerAccessDeniedHandler(accessDeniedHandlers));
        });
    }
}
