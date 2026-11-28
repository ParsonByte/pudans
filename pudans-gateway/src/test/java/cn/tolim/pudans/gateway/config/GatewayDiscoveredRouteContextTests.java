package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.InternalTokenGatewayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "pudans.iam.uri=http://iam.test:9090",
        "pudans.gateway.internal-token=test-internal-token"
})
@Import(GatewayDiscoveredRouteContextTests.DiscoveryTestConfiguration.class)
class GatewayDiscoveredRouteContextTests {

    @Autowired
    @Qualifier("gatewayRouteLocator")
    private RouteLocator gatewayRouteLocator;

    @Test
    void exposesRouteDeclaredInRegistryMetadata() {
        Route route = gatewayRouteLocator.getRoutes()
                .filter(candidate -> candidate.getId().equals("pudans-service-repository"))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://pudans-repository"));
        assertThat(route.getFilters()).hasSize(1);
        assertThat(route.getFilters().get(0)).isInstanceOf(InternalTokenGatewayFilter.class);
        assertThat(matches(route, "/apis/pudans.tolim.cn/v1alpha1/repository/files")).isTrue();
    }

    private static boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryTestConfiguration {

        @Bean
        @Primary
        DiscoveryClient discoveryClient() {
            DiscoveryClient client = mock(DiscoveryClient.class);
            ServiceInstance instance = mock(ServiceInstance.class);
            when(instance.getMetadata()).thenReturn(Map.of(
                    "gateway.path", "/apis/pudans.tolim.cn/v1alpha1/repository/**"));
            when(client.getServices()).thenReturn(List.of("pudans-repository"));
            when(client.getInstances("pudans-repository")).thenReturn(List.of(instance));
            return client;
        }
    }
}