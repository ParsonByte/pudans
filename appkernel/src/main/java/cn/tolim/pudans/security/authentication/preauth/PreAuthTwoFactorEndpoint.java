package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.exception.ProblemDetailFactory;
import cn.tolim.pudans.infra.utils.PudansUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
class PreAuthTwoFactorEndpoint {

    private static final String TWO_FACTOR_PAGE_REMOVED = "two-factor-page-removed";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> preAuthTwoFactorEndpoints() {
        return RouterFunctions.route()
                .GET(
                        "/challenges/two-factor/totp",
                        request -> ServerResponse.status(HttpStatus.GONE)
                                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                                .bodyValue(ProblemDetailFactory.create(
                                        HttpStatus.GONE,
                                        TWO_FACTOR_PAGE_REMOVED,
                                        "The two-factor page is not served by the backend.")))
                .before(PudansUtils.noCache())
                .build();
    }
}
