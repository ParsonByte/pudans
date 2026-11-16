package cn.tolim.pudans.security.authentication.switchuser;

import cn.tolim.pudans.infra.exception.ProblemDetailFactory;
import cn.tolim.pudans.security.authentication.PudansRedirectAuthenticationSuccessHandler;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.SwitchUserWebFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
class SwitchUserConfigurer implements SecurityConfigurer {

    private static final String IMPERSONATION_PAGE_REMOVED = "impersonation-page-removed";

    private final ReactiveUserDetailsService userDetailsService;

    SwitchUserConfigurer(ReactiveUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var successHandler = new PudansRedirectAuthenticationSuccessHandler("/auth/");
        var failureHandler = new RedirectServerAuthenticationFailureHandler("/login?error=impersonate");
        var filter = new SwitchUserWebFilter(userDetailsService, successHandler, failureHandler);
        http.addFilterAfter(filter, SecurityWebFiltersOrder.AUTHORIZATION);
    }

    @Bean
    RouterFunction<ServerResponse> switchUserPage() {
        return RouterFunctions.route()
                .GET("/login/impersonate", request -> removedPage())
                .GET("/logout/impersonate", request -> removedPage())
                .before(request -> {
                    request.exchange().getAttributes().put("cn.tolim.pudans.no-cache", true);
                    return request;
                })
                .build();
    }

    private static Mono<ServerResponse> removedPage() {
        return ServerResponse.status(HttpStatus.GONE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(ProblemDetailFactory.create(
                        HttpStatus.GONE,
                        IMPERSONATION_PAGE_REMOVED,
                        "The impersonation page is not served by the backend."));
    }
}
