package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.GroupVersion;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class CustomEndpointsBuilder {

    private final Map<GroupVersion, List<RouterFunction<ServerResponse>>> routerFunctionsMap;

    // 初始化按组版本索引的路由容器。
    CustomEndpointsBuilder() {
        routerFunctionsMap = new HashMap<>();
    }

    // 将端点按组版本归组，构建时统一挂载。
    CustomEndpointsBuilder add(CustomEndpoint customEndpoint) {
        routerFunctionsMap
                .computeIfAbsent(customEndpoint.groupVersion(), gv -> new LinkedList<>())
                .add(customEndpoint.endpoint());
        return this;
    }

    // 汇总各组路由并清空缓存，避免重复构建。
    RouterFunction<ServerResponse> build() {
        SpringdocRouteBuilder routeBuilder = SpringdocRouteBuilder.route();
        routerFunctionsMap.forEach((gv, routerFunctions) -> routeBuilder.nest(
                RequestPredicates.path("/apis/" + gv),
                () -> routerFunctions.stream().reduce(RouterFunction::and).orElse(null)));
        if (routerFunctionsMap.isEmpty()) {
            return request -> Mono.empty();
        }
        routerFunctionsMap.clear();
        return routeBuilder.build();
    }
}
