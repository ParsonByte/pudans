package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.InternalTokenGatewayFilter;
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
        "pudans.gateway.internal-token=test-internal-token",
        "pudans.gateway.services.repository.uri=http://repository.test:8091",
        "pudans.gateway.services.repository.path=/apis/pudans.tolim.cn/v1alpha1/repository/**",
        "pudans.gateway.services.notification.uri=http://notification.test:8092",
        "pudans.gateway.services.notification.path=/apis/pudans.tolim.cn/v1alpha1/notification/**",
        "pudans.gateway.services.mq.uri=http://mq.test:8093",
        "pudans.gateway.services.mq.path=/apis/pudans.tolim.cn/v1alpha1/mq/**",
        "pudans.gateway.services.job.uri=http://job.test:8094",
        "pudans.gateway.services.job.path=/apis/pudans.tolim.cn/v1alpha1/job/**",
        "pudans.gateway.services.search.uri=http://search.test:8095",
        "pudans.gateway.services.search.path=/apis/pudans.tolim.cn/v1alpha1/search/**"
})
class GatewayServiceRouteConfigTests {

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

    private static String serviceRouteId(String code) {
        return GatewayRouteConfig.SERVICE_ROUTE_ID_PREFIX + code;
    }

    @Test
    void repositoryServiceRouteMatchesItsApiAndInjectsInternalToken() {
        Route route = routesById().get(serviceRouteId("repository"));

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("http://repository.test:8091"));
        assertThat(route.getOrder()).isEqualTo(GatewayProperties.DEFAULT_SERVICE_ROUTE_ORDER);
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/repository/files")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/repository/files/f-1/content")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/repositoryx/files")).isFalse();
        assertInternalTokenFilter(route);
    }

    @Test
    void notificationServiceRouteMatchesItsApiPath() {
        Map<String, Route> routes = routesById();
        Route route = routes.get(serviceRouteId("notification"));

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("http://notification.test:8092"));
        assertThat(route.getOrder()).isEqualTo(GatewayProperties.DEFAULT_SERVICE_ROUTE_ORDER);
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/notification/send")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/notification/records")).isTrue();
        assertInternalTokenFilter(route);

        Route iamRoute = routes.get(GatewayRouteConfig.IAM_ROUTE_ID);
        assertThat(iamRoute).isNotNull();
        assertThat(matches(iamRoute, "/apis/pudans.tolim.cn/v1alpha1/notification/send")).isFalse();
    }

    @Test
    void mqServiceRouteUsesNamespacedApiPathWithoutStripPrefix() {
        Route route = routesById().get(serviceRouteId("mq"));

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("http://mq.test:8093"));
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/mq/messages")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/mq/dead-letters")).isTrue();
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/mqx/messages")).isFalse();
        assertThat(route.getFilters()).hasSize(1);
        assertInternalTokenFilter(route);
    }

    @Test
    void jobAndSearchServiceRoutesMatchTheirApis() {
        Map<String, Route> routes = routesById();

        Route job = routes.get(serviceRouteId("job"));
        assertThat(job).isNotNull();
        assertThat(job.getUri()).isEqualTo(URI.create("http://job.test:8094"));
        assertThat(matches(job, "/apis/pudans.tolim.cn/v1alpha1/job/jobs")).isTrue();
        assertThat(matches(job, "/apis/pudans.tolim.cn/v1alpha1/job/executions")).isTrue();
        assertInternalTokenFilter(job);

        Route search = routes.get(serviceRouteId("search"));
        assertThat(search).isNotNull();
        assertThat(search.getUri()).isEqualTo(URI.create("http://search.test:8095"));
        assertThat(matches(search, "/apis/pudans.tolim.cn/v1alpha1/search/query")).isTrue();
        assertThat(matches(search, "/apis/pudans.tolim.cn/v1alpha1/search/indexes")).isTrue();
        assertInternalTokenFilter(search);
    }

    private static void assertInternalTokenFilter(Route route) {
        assertThat(route.getFilters()).hasSize(1);
        assertThat(route.getFilters().get(0)).isInstanceOf(InternalTokenGatewayFilter.class);
        InternalTokenGatewayFilter filter = (InternalTokenGatewayFilter) route.getFilters().get(0);
        assertThat(filter.getToken()).isEqualTo("test-internal-token");
        assertThat(filter.getOrder()).isGreaterThan(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}