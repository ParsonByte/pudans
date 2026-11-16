package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.SchemeManager;
import cn.tolim.pudans.core.rbac.BuiltinEntityDefinitions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
@Order(0)
public class EndpointApiGroupSelfCheck implements ApplicationRunner {

    private static final Set<String> ALLOWED_API_GROUPS = ApiGroups.ALL;

    private static final Set<String> EXPLICITLY_ALLOWED_RESOURCES =
            Set.of("system", "login", "recovery", "oauth2", "passkeys");

    private static final String API_GROUP_ALL = "*";

    private static final Pattern API_GROUP_PATTERN = Pattern.compile(
            "^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)+$");

    private static final Pattern API_VERSION_PATTERN =
            Pattern.compile("^v[0-9]+(?:(?:alpha|beta)[0-9]+)?$");

    private static final Pattern ROUTE_PATH_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_-])(/[A-Za-z0-9_{}.*-]+)");

    private final ApplicationContext applicationContext;

    public EndpointApiGroupSelfCheck(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var endpoints = applicationContext.getBeansOfType(CustomEndpoint.class).values();
        var routeErrors = new ArrayList<String>();
        var allRoutes = new TreeSet<String>();

        for (var endpoint : endpoints) {
            var groupVersion = endpoint.groupVersion();
            if (groupVersion == null) {
                routeErrors.add(endpoint.getClass().getName() + " returned a null groupVersion.");
                continue;
            }
            if (!API_GROUP_PATTERN.matcher(groupVersion.group()).matches()) {
                routeErrors.add(endpoint.getClass().getName() + " has invalid API group '"
                        + groupVersion.group() + "'. Expected a DNS-like group containing a dot.");
            }
            if (!ALLOWED_API_GROUPS.contains(groupVersion.group())) {
                routeErrors.add(endpoint.getClass().getName() + " uses non-canonical API group '"
                        + groupVersion.group() + "'. Allowed groups: " + ALLOWED_API_GROUPS);
            }
            if (!API_VERSION_PATTERN.matcher(groupVersion.version()).matches()) {
                routeErrors.add(endpoint.getClass().getName() + " has invalid API version '"
                        + groupVersion.version() + "'.");
            }

            var routePathErrors = new ArrayList<String>();
            var routes = extractRelativePaths(
                    endpoint.endpoint(), endpoint.getClass().getName(), routePathErrors);
            routeErrors.addAll(routePathErrors);
            if (routes.isEmpty() && routePathErrors.isEmpty()) {
                routeErrors.add(endpoint.getClass().getName() + " exposes no route path.");
                continue;
            }
            for (var route : routes) {
                allRoutes.add(route);
                if (!route.startsWith("/")) {
                    routeErrors.add(endpoint.getClass().getName() + " route '" + route
                            + "' must start with '/'.");
                } else if (route.startsWith("/api/") || route.startsWith("/apis/")) {
                    routeErrors.add(endpoint.getClass().getName() + " route '" + route
                            + "' must be relative; the /apis/{group}/{version} prefix is added by the endpoint builder.");
                }
            }
        }

        if (!routeErrors.isEmpty()) {
            throw new IllegalStateException("Invalid custom endpoint route registration:\n - "
                    + String.join("\n - ", routeErrors));
        }

        var coveredResources = coveredResources();
        var schemeManager = applicationContext.getBeanProvider(SchemeManager.class).getIfAvailable();
        if (schemeManager != null) {
            schemeManager.schemes().stream()
                    .map(scheme -> "/" + scheme.plural())
                    .forEach(allRoutes::add);
        }
        var uncoveredPaths = allRoutes.stream()
                .filter(route -> !route.startsWith("/api/") && !route.startsWith("/apis/"))
                .filter(route -> {
                    var resource = topLevelResource(route);
                    return !coveredResources.contains(resource) && !EXPLICITLY_ALLOWED_RESOURCES.contains(resource);
                })
                .collect(Collectors.toCollection(TreeSet::new));
        if (!uncoveredPaths.isEmpty()) {
            throw new IllegalStateException(
                    "The following custom endpoint paths are not covered by a built-in role rule or an explicit"
                            + " permit-all rule: "
                            + uncoveredPaths
                            + ". Add a matching resource to BuiltinEntityDefinitions and to the role-template-*.yaml"
                            + " files, or add the path to the explicit permit-all list. Covered resources: "
                            + coveredResources);
        }
    }

    private static Set<String> extractRelativePaths(
            RouterFunction<ServerResponse> routerFunction,
            String endpointName,
            List<String> pathErrors) {
        var routes = new LinkedHashSet<String>();
        Deque<String> nestedPrefixes = new ArrayDeque<>();
        routerFunction.accept(new RouterFunctions.Visitor() {
            @Override
            public void startNested(RequestPredicate predicate) {
                var paths = pathsOf(predicate);
                nestedPrefixes.addLast(paths.stream().findFirst().orElse(""));
            }

            @Override
            public void endNested(RequestPredicate predicate) {
                if (!nestedPrefixes.isEmpty()) {
                    nestedPrefixes.removeLast();
                }
            }

            @Override
            public void route(RequestPredicate predicate, HandlerFunction<?> handler) {
                var text = predicate.toString();
                var paths = pathsOf(predicate);
                if (paths.isEmpty()) {
                    if (nestedPrefixes.isEmpty() || nestedPrefixes.stream().allMatch(String::isEmpty)) {
                        pathErrors.add(endpointName + " route predicate has no absolute path: " + text);
                        return;
                    }
                    routes.add(String.join("", nestedPrefixes));
                    return;
                }
                var prefix = String.join("", nestedPrefixes);
                paths.forEach(path -> routes.add(prefix + path));
            }

            @Override
            public void resources(
                    java.util.function.Function<org.springframework.web.reactive.function.server.ServerRequest,
                            reactor.core.publisher.Mono<org.springframework.core.io.Resource>> lookupFunction) {
                // No-op.
            }

            @Override
            public void attributes(Map<String, Object> attributes) {
                // No-op.
            }

            @Override
            public void unknown(RouterFunction<?> router) {
                // No-op.
            }
        });
        return routes;
    }

    private static Set<String> pathsOf(RequestPredicate predicate) {
        var paths = new LinkedHashSet<String>();
        var matcher = ROUTE_PATH_PATTERN.matcher(predicate.toString());
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    private static Set<String> coveredResources() {
        return Stream.of(
                        BuiltinEntityDefinitions.systemAdminRole(), BuiltinEntityDefinitions.normalRole())
                .flatMap(role -> role.getRules().stream())
                .filter(rule -> Arrays.asList(rule.getApiGroups()).contains(ApiGroups.API))
                .flatMap(rule -> Arrays.stream(rule.getResources()))
                .filter(StringUtils::hasText)
                .filter(resource -> !API_GROUP_ALL.equals(resource))
                .map(resource -> {
                    var separator = resource.indexOf('/');
                    return separator < 0 ? resource : resource.substring(0, separator);
                })
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String topLevelResource(String route) {
        var path = route.startsWith("/") ? route.substring(1) : route;
        var separator = path.indexOf('/');
        return separator < 0 ? path : path.substring(0, separator);
    }
}
