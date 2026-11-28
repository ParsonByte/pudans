package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.InternalTokenGatewayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayDiscoveryRouteLocatorTests {

    private static final String REPOSITORY_PATH =
            "/apis/pudans.tolim.cn/v1alpha1/repository/**";

    @Test
    void readsRouteDeclarationFromServiceMetadata() {
        ServiceInstance instance = instance(Map.of(
                "gateway.path", REPOSITORY_PATH,
                "gateway.code", "repository",
                "gateway.order", "-35",
                "gateway.strip-prefix", "true"));

        GatewayDiscoveryRouteLocator.ServiceRouteDefinition definition =
                GatewayDiscoveryRouteLocator.definition("pudans-repository", instance);

        assertThat(definition.code()).isEqualTo("repository");
        assertThat(definition.path()).isEqualTo(REPOSITORY_PATH);
        assertThat(definition.order()).isEqualTo(-35);
        assertThat(definition.stripPrefix()).isTrue();
        assertThat(definition.enabled()).isTrue();
    }

    @Test
    void ignoresServicesWithoutPathMetadata() {
        GatewayDiscoveryRouteLocator.ServiceRouteDefinition definition =
                GatewayDiscoveryRouteLocator.definition("pudans-echo", instance(Map.of()));

        assertThat(definition.enabled()).isFalse();
        assertThat(definition.path()).isNull();
    }

    @Test
    void metadataCanDisableService() {
        GatewayDiscoveryRouteLocator.ServiceRouteDefinition definition =
                GatewayDiscoveryRouteLocator.definition("pudans-echo",
                        instance(Map.of("gateway.path", "/apis/pudans.tolim.cn/v1alpha1/echo/**",
                                "gateway.enabled", "false")));

        assertThat(definition.enabled()).isFalse();
    }

    @Test
    void buildsDiscoveredRouteWithInternalToken() {
        GatewayProperties properties = new GatewayProperties();
        properties.setInternalToken("test-internal-token");
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance instance = instance(Map.of("gateway.path", REPOSITORY_PATH));
        when(discoveryClient.getServices()).thenReturn(List.of("pudans-repository"));
        when(discoveryClient.getInstances("pudans-repository")).thenReturn(List.of(instance));

        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(PathRoutePredicateFactory.class,
                () -> new PathRoutePredicateFactory(new WebFluxProperties()));
        context.refresh();
        GatewayDiscoveryRouteLocator locator = new GatewayDiscoveryRouteLocator(
                new RouteLocatorBuilder(context), properties, discoveryClient);
        List<Route> routes = locator.locate().getRoutes().collectList().block();

        assertThat(routes).hasSize(1);
        Route route = routes.get(0);
        assertThat(route.getId()).isEqualTo("pudans-service-repository");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://pudans-repository"));
        assertThat(route.getFilters()).hasSize(1);
        assertThat(route.getFilters().get(0)).isInstanceOf(InternalTokenGatewayFilter.class);
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/repository/files")).isTrue();
    }

    private static ServiceInstance instance(Map<String, String> metadata) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getMetadata()).thenReturn(metadata);
        return instance;
    }

    private static boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }
}