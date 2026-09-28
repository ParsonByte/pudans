package cn.tolim.pudans.security.authentication.switchuser;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.infra.actuator.GlobalInfoService;
import cn.tolim.pudans.security.authentication.PudansRedirectAuthenticationSuccessHandler;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.SwitchUserWebFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;


@Component
class SwitchUserConfigurer implements SecurityConfigurer {

    private final ReactiveUserDetailsService userDetailsService;

    SwitchUserConfigurer(ReactiveUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var successHandler = new PudansRedirectAuthenticationSuccessHandler("/console");
        var failureHandler = new RedirectServerAuthenticationFailureHandler("/login?error=impersonate");
        var filter = new SwitchUserWebFilter(userDetailsService, successHandler, failureHandler);
        http.addFilterAfter(filter, SecurityWebFiltersOrder.AUTHORIZATION);
    }

    @Bean
    RouterFunction<ServerResponse> switchUserPage(UserService userService, GlobalInfoService globalInfoService) {
        return RouterFunctions.route()
                .GET("/login/impersonate", request -> {
                    var username = request.queryParam("username")
                            .filter(name -> !name.isBlank())
                            .orElseThrow(() -> new ServerWebInputException("Query parameter username is required."));
                    var contextPath = request.exchange()
                            .getRequest()
                            .getPath()
                            .contextPath()
                            .value();
                    var action = contextPath + "/login/impersonate?username="
                            + URLEncoder.encode(username, StandardCharsets.UTF_8);
                    return userService
                            .getUser(username)
                            .flatMap(user -> ServerResponse.ok()
                                    .render(
                                            "switch-user",
                                            Map.of(
                                                    "globalInfo",
                                                    globalInfoService.getGlobalInfo(),
                                                    "action",
                                                    action,
                                                    "user",
                                                    user,
                                                    "exit",
                                                    false)));
                })
                .GET("/logout/impersonate", request -> {
                    var contextPath = request.exchange()
                            .getRequest()
                            .getPath()
                            .contextPath()
                            .value();
                    return ReactiveSecurityContextHolder.getContext()
                            .map(SecurityContext::getAuthentication)
                            .map(Authentication::getName)
                            .flatMap(userService::getUser)
                            .flatMap(user -> ServerResponse.ok()
                                    .render(
                                            "switch-user",
                                            Map.of(
                                                    "globalInfo",
                                                    globalInfoService.getGlobalInfo(),
                                                    "action",
                                                    contextPath + "/logout/impersonate",
                                                    "user",
                                                    user,
                                                    "exit",
                                                    true)));
                })
                .before(request -> {
                    request.exchange().getAttributes().put("cn.tolim.pudans.no-cache", true);
                    return request;
                })
                .build();
    }
}
