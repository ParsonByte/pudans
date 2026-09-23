package cn.tolim.pudans.security.authentication.preauth;

import cn.tolim.pudans.infra.actuator.GlobalInfoService;
import cn.tolim.pudans.infra.utils.PudansUtils;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;


@Component
class PreAuthTwoFactorEndpoint {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> preAuthTwoFactorEndpoints(GlobalInfoService globalInfoService) {
        return RouterFunctions.route()
                .GET(
                        "/challenges/two-factor/totp",
                        request -> ServerResponse.ok()
                                .render(
                                        "challenges/two-factor/totp",
                                        Map.of("globalInfo", globalInfoService.getGlobalInfo())))
                .before(PudansUtils.noCache())
                .build();
    }
}
