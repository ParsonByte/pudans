package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.PublicPathPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "pudans.iam.uri=http://iam.test:9090",
        "pudans.gateway.frontend.home-uri=http://home.test:3000",
        "pudans.gateway.frontend.login-uri=http://login.test:3000",
        "pudans.gateway.apps.order.uri=http://order.test:8082"
})
class GatewayRouteConfigTests {

    @Autowired
    @Qualifier("gatewayRouteLocator")
    private RouteLocator gatewayRouteLocator;

    private static boolean matches(Route route, String path) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    private Map<String, Route> routesById() {
        List<Route> routes = gatewayRouteLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        return routes.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
    }

    @Test
    void iamRouteUsesConfiguredUriWithoutStripPrefix() {
        Route route = routesById().get(GatewayRouteConfig.IAM_ROUTE_ID);

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("http://iam.test:9090"));
        assertThat(route.getFilters()).isEmpty();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/iam/users")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/iam/auth/login")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/other")).isFalse();
    }

    @Test
    void iamAuthSessionAndTicketRoutesMatchTheirApis() {
        Map<String, Route> routes = routesById();

        Route auth = routes.get(GatewayRouteConfig.IAM_AUTH_ROUTE_ID);
        assertThat(auth).isNotNull();
        assertThat(auth.getUri()).isEqualTo(URI.create("http://iam.test:9090"));
        assertThat(matches(auth, "/apis/pudans.tolim.cn/v1alpha1/iam/auth/login")).isTrue();
        assertThat(matches(auth, "/apis/pudans.tolim.cn/v1alpha1/iam/auth/logout")).isTrue();
        assertThat(matches(auth, "/apis/pudans.tolim.cn/v1alpha1/iam/users")).isFalse();

        Route session = routes.get(GatewayRouteConfig.IAM_SESSION_ROUTE_ID);
        assertThat(session).isNotNull();
        assertThat(matches(session, PublicPathPolicy.SESSION_VALIDATE_PATH)).isTrue();
        assertThat(matches(session, PublicPathPolicy.SESSIONS_VALIDATE_PATH)).isTrue();

        Route ticket = routes.get(GatewayRouteConfig.IAM_TICKET_ROUTE_ID);
        assertThat(ticket).isNotNull();
        assertThat(matches(ticket, PublicPathPolicy.TICKET_ISSUE_PATH)).isTrue();
        assertThat(matches(ticket, PublicPathPolicy.TICKET_EXCHANGE_PATH)).isTrue();
        assertThat(matches(ticket, "/apis/pudans.tolim.cn/v1alpha1/iam/users")).isFalse();
    }

    @Test
    void homeAndLoginRoutesTargetFrontend() {
        Map<String, Route> routes = routesById();

        Route home = routes.get(GatewayRouteConfig.HOME_ROUTE_ID);
        assertThat(home).isNotNull();
        assertThat(home.getUri()).isEqualTo(URI.create("http://home.test:3000"));
        assertThat(matches(home, PublicPathPolicy.HOME_PATH)).isTrue();
        assertThat(matches(home, "/login")).isFalse();

        Route assets = routes.get(GatewayRouteConfig.HOME_ASSETS_ROUTE_ID);
        assertThat(assets).isNotNull();
        assertThat(matches(assets, "/assets/app.js")).isTrue();
        assertThat(matches(assets, "/favicon.ico")).isTrue();
        assertThat(matches(assets, "/site/other.js")).isFalse();

        Route login = routes.get(GatewayRouteConfig.LOGIN_ROUTE_ID);
        assertThat(login).isNotNull();
        assertThat(login.getUri()).isEqualTo(URI.create("http://login.test:3000"));
        assertThat(matches(login, "/login")).isTrue();
        assertThat(matches(login, "/login/")).isTrue();
        assertThat(matches(login, "/login/mfa")).isTrue();
        assertThat(matches(login, "/loginx")).isFalse();
    }

    @Test
    void applicationRoutesCoverPagesCallbacksAndApis() {
        Map<String, Route> routes = routesById();
        String pageRouteId = GatewayRouteConfig.APP_ROUTE_ID_PREFIX + "order";
        String apiRouteId = pageRouteId + GatewayRouteConfig.APP_API_ROUTE_SUFFIX;

        Route pageRoute = routes.get(pageRouteId);
        assertThat(pageRoute).isNotNull();
        assertThat(pageRoute.getUri()).isEqualTo(URI.create("http://order.test:8082"));
        assertThat(pageRoute.getFilters()).hasSize(1);
        assertThat(matches(pageRoute, "/apps/order")).isTrue();
        assertThat(matches(pageRoute, "/apps/order/orders/42")).isTrue();
        assertThat(matches(pageRoute, "/apps/order/sso/callback")).isTrue();
        assertThat(matches(pageRoute, "/apps/billing/orders/42")).isFalse();

        Route apiRoute = routes.get(apiRouteId);
        assertThat(apiRoute).isNotNull();
        assertThat(apiRoute.getUri()).isEqualTo(URI.create("http://order.test:8082"));
        assertThat(apiRoute.getFilters()).isEmpty();
        assertThat(matches(apiRoute, "/apis/pudans.tolim.cn/v1alpha1/order/orders")).isTrue();
        assertThat(matches(apiRoute, "/apis/pudans.tolim.cn/v1alpha1/orderx/users")).isFalse();
    }

    @Test
    void unknownPathsAreNotMatchedByAnyRoute() {
        List<Route> routes = gatewayRouteLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(routes.stream().noneMatch(route -> matches(route, "/other/path"))).isTrue();
        assertThat(routes.stream().noneMatch(route -> matches(route, "/actuator/health"))).isTrue();
        assertThat(routes.stream().noneMatch(route -> matches(route, "/apps/billing"))).isTrue();
    }
}