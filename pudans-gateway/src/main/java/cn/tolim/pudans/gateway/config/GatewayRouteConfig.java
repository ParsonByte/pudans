package cn.tolim.pudans.gateway.config;

import cn.tolim.pudans.gateway.filter.InternalTokenGatewayFilter;
import cn.tolim.pudans.gateway.filter.PublicPathPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
@EnableScheduling
public class GatewayRouteConfig {

    public static final String API_BASE_PATH = PublicPathPolicy.API_BASE_PATH;

    public static final String IAM_ROUTE_ID = "pudans-iam";
    public static final String IAM_ROUTE_PATH = API_BASE_PATH + "/iam/**";
    public static final String IAM_AUTH_ROUTE_ID = "pudans-iam-auth";
    public static final String IAM_AUTH_ROUTE_PATH = API_BASE_PATH + "/iam/auth/**";
    public static final String IAM_SESSION_ROUTE_ID = "pudans-iam-session";
    public static final String[] IAM_SESSION_ROUTE_PATHS = {
            API_BASE_PATH + "/iam/session/**",
            API_BASE_PATH + "/iam/sessions/**"};
    public static final String IAM_TICKET_ROUTE_ID = "pudans-iam-ticket";
    public static final String[] IAM_TICKET_ROUTE_PATHS = {
            API_BASE_PATH + "/iam/ticket/**",
            API_BASE_PATH + "/iam/tickets/**"};

    public static final String HOME_ROUTE_ID = "pudans-home";
    public static final String HOME_ROUTE_PATH = PublicPathPolicy.HOME_PATH;
    public static final String HOME_ASSETS_ROUTE_ID = "pudans-home-assets";
    public static final String[] HOME_ASSETS_ROUTE_PATHS = {
            "/assets/**",
            "/static/**",
            "/favicon.ico",
            "/robots.txt",
            "/manifest.json",
            "/site.webmanifest"};
    public static final String LOGIN_ROUTE_ID = "pudans-login";
    public static final String[] LOGIN_ROUTE_PATHS = {
            PublicPathPolicy.LOGIN_PAGE_PATH,
            PublicPathPolicy.LOGIN_PAGE_PREFIX + "**"};

    public static final String APP_ROUTE_ID_PREFIX = "pudans-app-";
    public static final String APP_API_ROUTE_SUFFIX = "-api";
    public static final String APP_DEFAULT_PATH_PREFIX = PublicPathPolicy.APP_PATH_PREFIX;
    public static final String SERVICE_ROUTE_ID_PREFIX = "pudans-service-";
    public static final String RESERVED_APP_CODE = "iam";

    static final Pattern APP_CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final String PATH_WILDCARD_SUFFIX = "/**";

    // Rebuilt after a Nacos config refresh so static routes pick up new targets
    @Bean
    @RefreshScope
    public RouteLocator gatewayRouteLocator(RouteLocatorBuilder builder,
                                            GatewayProperties properties,
                                            @Value("${pudans.iam.uri:lb://pudans-iam}") String iamUri,
                                            ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        RouteLocator staticRoutes = buildStaticRoutes(builder, properties, iamUri);
        DiscoveryClient discoveryClient = selectDiscoveryClient(discoveryClientProvider);
        if (discoveryClient == null) {
            return staticRoutes;
        }
        GatewayDiscoveryRouteLocator discoveryRoutes =
                new GatewayDiscoveryRouteLocator(builder, properties, discoveryClient);
        return () -> Flux.concat(staticRoutes.getRoutes(), discoveryRoutes.locate().getRoutes());
    }

    static DiscoveryClient selectDiscoveryClient(ObjectProvider<DiscoveryClient> provider) {
        return provider.orderedStream()
                .filter(CompositeDiscoveryClient.class::isInstance)
                .findFirst()
                .orElseGet(() -> provider.orderedStream().findFirst().orElse(null));
    }

    static RouteLocator buildStaticRoutes(RouteLocatorBuilder builder, GatewayProperties properties,
                                          String iamUri) {
        GatewayProperties.Frontend frontend = properties.getFrontend();
        RouteLocatorBuilder.Builder routes = builder.routes()
                .route(IAM_AUTH_ROUTE_ID, route -> route
                        .order(-30)
                        .path(IAM_AUTH_ROUTE_PATH)
                        .uri(iamUri))
                .route(IAM_SESSION_ROUTE_ID, route -> route
                        .order(-20)
                        .path(IAM_SESSION_ROUTE_PATHS)
                        .uri(iamUri))
                .route(IAM_TICKET_ROUTE_ID, route -> route
                        .order(-10)
                        .path(IAM_TICKET_ROUTE_PATHS)
                        .uri(iamUri))
                .route(IAM_ROUTE_ID, route -> route
                        .path(IAM_ROUTE_PATH)
                        .uri(iamUri));
        if (hasText(frontend.getHomeUri())) {
            routes.route(HOME_ROUTE_ID, route -> route
                    .path(HOME_ROUTE_PATH)
                    .uri(frontend.getHomeUri()));
            routes.route(HOME_ASSETS_ROUTE_ID, route -> route
                    .path(HOME_ASSETS_ROUTE_PATHS)
                    .uri(frontend.getHomeUri()));
        }
        if (hasText(frontend.getLoginUri())) {
            routes.route(LOGIN_ROUTE_ID, route -> route
                    .path(LOGIN_ROUTE_PATHS)
                    .uri(frontend.getLoginUri()));
        }
        Map<String, GatewayProperties.App> apps = properties.getApps();
        if (apps != null) {
            apps.forEach((code, app) -> addApplicationRoutes(routes, code, app));
        }
        addServiceRoutes(routes, properties);
        return routes.build();
    }

    private static void addApplicationRoutes(RouteLocatorBuilder.Builder routes, String code,
                                             GatewayProperties.App app) {
        if (!hasText(code) || RESERVED_APP_CODE.equals(code)) {
            throw new IllegalStateException("pudans.gateway.apps must not contain an empty or reserved app code");
        }
        if (!APP_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalStateException("pudans.gateway.apps." + code + " is not a valid app code");
        }
        if (!hasText(app.getUri())) {
            throw new IllegalStateException("pudans.gateway.apps." + code + ".uri must not be blank");
        }
        String pathPrefix = normalizePathPrefix(code, app.getPathPrefix());
        int stripSegments = countSegments(pathPrefix);
        routes.route(APP_ROUTE_ID_PREFIX + code, route -> {
            if (app.isStripPrefix() && stripSegments > 0) {
                return route.path(pathPrefix + "/**")
                        .filters(filters -> filters.stripPrefix(stripSegments))
                        .uri(app.getUri());
            }
            return route.path(pathPrefix + "/**").uri(app.getUri());
        });
        String apiPath = hasText(app.getApiPath())
                ? app.getApiPath()
                : API_BASE_PATH + "/" + code + "/**";
        routes.route(APP_ROUTE_ID_PREFIX + code + APP_API_ROUTE_SUFFIX, route -> route
                .path(apiPath)
                .uri(app.getUri()));
    }

    private static void addServiceRoutes(RouteLocatorBuilder.Builder routes, GatewayProperties properties) {
        Map<String, GatewayProperties.Service> services = properties.getServices();
        if (services == null || services.isEmpty()) {
            return;
        }
        if (!hasText(properties.getInternalToken())) {
            throw new IllegalStateException(
                    "pudans.gateway.internal-token must not be blank when internal services are configured");
        }
        InternalTokenGatewayFilter tokenFilter =
                new InternalTokenGatewayFilter(properties.getInternalToken());
        services.forEach((code, service) -> addServiceRoute(routes, code, service, tokenFilter));
    }

    private static void addServiceRoute(RouteLocatorBuilder.Builder routes, String code,
                                        GatewayProperties.Service service,
                                        InternalTokenGatewayFilter tokenFilter) {
        if (service == null || !service.isEnabled()) {
            return;
        }
        if (!hasText(code) || !APP_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalStateException("pudans.gateway.services." + code + " is not a valid service code");
        }
        if (!hasText(service.getUri())) {
            throw new IllegalStateException("pudans.gateway.services." + code + ".uri must not be blank");
        }
        if (!hasText(service.getPath()) || !service.getPath().trim().startsWith("/")) {
            throw new IllegalStateException(
                    "pudans.gateway.services." + code + ".path must be an absolute path pattern");
        }
        String path = service.getPath().trim();
        routes.route(SERVICE_ROUTE_ID_PREFIX + code, route -> {
            BooleanSpec spec = route.order(service.getOrder()).path(path);
            if (service.isStripPrefix()) {
                int stripSegments = countSegments(stripWildcardSuffix(path));
                if (stripSegments > 0) {
                    return spec.filters(filters -> filters.stripPrefix(stripSegments).filter(tokenFilter))
                            .uri(service.getUri());
                }
            }
            return spec.filters(filters -> filters.filter(tokenFilter)).uri(service.getUri());
        });
    }

    static String stripWildcardSuffix(String path) {
        if (path.endsWith(PATH_WILDCARD_SUFFIX)) {
            return path.substring(0, path.length() - PATH_WILDCARD_SUFFIX.length());
        }
        return path;
    }

    private static String normalizePathPrefix(String code, String pathPrefix) {
        String prefix = hasText(pathPrefix) ? pathPrefix.trim() : APP_DEFAULT_PATH_PREFIX + code;
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        return prefix;
    }

    static int countSegments(String path) {
        int segments = 0;
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                segments++;
            }
        }
        return segments;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}