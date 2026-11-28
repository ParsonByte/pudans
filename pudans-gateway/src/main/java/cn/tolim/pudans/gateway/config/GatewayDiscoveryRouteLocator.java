package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.InternalTokenGatewayFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

// Builds internal service routes from the route declaration published in registry metadata
final class GatewayDiscoveryRouteLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayDiscoveryRouteLocator.class);

    private static final String SERVICE_ID_PREFIX = "pudans-";
    private static final String IAM_SERVICE_ID = "pudans-iam";
    private static final String GATEWAY_SERVICE_ID = "pudans-gateway";
    private static final String META_PATH = "gateway.path";
    private static final String META_CODE = "gateway.code";
    private static final String META_ORDER = "gateway.order";
    private static final String META_STRIP_PREFIX = "gateway.strip-prefix";
    private static final String META_ENABLED = "gateway.enabled";

    private final RouteLocatorBuilder builder;
    private final GatewayProperties properties;
    private final DiscoveryClient discoveryClient;
    private final InternalTokenGatewayFilter tokenFilter;

    private volatile int lastRouteCount = -1;

    GatewayDiscoveryRouteLocator(RouteLocatorBuilder builder, GatewayProperties properties,
                                 DiscoveryClient discoveryClient) {
        if (!GatewayRouteConfig.hasText(properties.getInternalToken())) {
            throw new IllegalStateException(
                    "pudans.gateway.internal-token must not be blank for discovery routes");
        }
        this.builder = builder;
        this.properties = properties;
        this.discoveryClient = discoveryClient;
        this.tokenFilter = new InternalTokenGatewayFilter(properties.getInternalToken());
    }

    // Route locator that re-reads the registry on every refresh
    RouteLocator locate() {
        return () -> Mono.fromCallable(this::buildRoutes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(exception -> {
                    LOGGER.warn("Refreshing discovered service routes failed: {}",
                            exception.getMessage());
                    return Flux.empty();
                });
    }

    private List<Route> buildRoutes() {
        List<String> serviceIds = discoveryClient.getServices();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        Map<String, GatewayProperties.Service> configured = properties.getServices() == null
                ? Map.of() : properties.getServices();
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (String serviceId : serviceIds) {
            if (!isEligible(serviceId)) {
                continue;
            }
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            if (instances == null || instances.isEmpty()) {
                continue;
            }
            ServiceRouteDefinition definition = definition(serviceId, instances.get(0));
            if (!definition.enabled() || configured.containsKey(definition.code())) {
                continue;
            }
            addRoute(routes, serviceId, definition);
        }
        List<Route> discovered = routes.build().getRoutes().collectList().block();
        List<Route> result = discovered == null ? List.of() : discovered;
        if (result.size() != lastRouteCount) {
            LOGGER.info("Discovered {} service routes from the registry", result.size());
            lastRouteCount = result.size();
        }
        return result;
    }

    private static boolean isEligible(String serviceId) {
        return GatewayRouteConfig.hasText(serviceId)
                && serviceId.startsWith(SERVICE_ID_PREFIX)
                && !IAM_SERVICE_ID.equals(serviceId)
                && !GATEWAY_SERVICE_ID.equals(serviceId);
    }

    // Reads the route declaration a service publishes through registry metadata
    static ServiceRouteDefinition definition(String serviceId, ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata() == null
                ? Map.of() : instance.getMetadata();
        String defaultCode = serviceId.startsWith(SERVICE_ID_PREFIX)
                ? serviceId.substring(SERVICE_ID_PREFIX.length()) : serviceId;
        String code = GatewayRouteConfig.hasText(metadata.get(META_CODE))
                ? metadata.get(META_CODE).trim() : defaultCode;
        boolean enabled = Boolean.parseBoolean(metadata.getOrDefault(META_ENABLED, "true"));
        String path = metadata.get(META_PATH);
        if (!GatewayRouteConfig.hasText(path) || !path.trim().startsWith("/")
                || !GatewayRouteConfig.APP_CODE_PATTERN.matcher(code).matches()) {
            return new ServiceRouteDefinition(code, null,
                    GatewayProperties.DEFAULT_SERVICE_ROUTE_ORDER, false, false);
        }
        int order = parseInt(metadata.get(META_ORDER), GatewayProperties.DEFAULT_SERVICE_ROUTE_ORDER);
        boolean stripPrefix = Boolean.parseBoolean(
                metadata.getOrDefault(META_STRIP_PREFIX, "false"));
        return new ServiceRouteDefinition(code, path.trim(), order, stripPrefix, enabled);
    }

    private void addRoute(RouteLocatorBuilder.Builder routes, String serviceId,
                          ServiceRouteDefinition definition) {
        routes.route(GatewayRouteConfig.SERVICE_ROUTE_ID_PREFIX + definition.code(), route -> {
            BooleanSpec spec = route.order(definition.order()).path(definition.path());
            String uri = "lb://" + serviceId;
            if (definition.stripPrefix()) {
                int stripSegments = GatewayRouteConfig.countSegments(
                        GatewayRouteConfig.stripWildcardSuffix(definition.path()));
                if (stripSegments > 0) {
                    return spec.filters(filters -> filters.stripPrefix(stripSegments)
                            .filter(tokenFilter)).uri(uri);
                }
            }
            return spec.filters(filters -> filters.filter(tokenFilter)).uri(uri);
        });
        LOGGER.debug("Discovered route {} -> {} for service {}", definition.code(),
                definition.path(), serviceId);
    }

    private static int parseInt(String value, int fallback) {
        if (!GatewayRouteConfig.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    // Parsed route declaration from one service instance
    record ServiceRouteDefinition(String code, String path, int order, boolean stripPrefix,
                                  boolean enabled) {
    }
}