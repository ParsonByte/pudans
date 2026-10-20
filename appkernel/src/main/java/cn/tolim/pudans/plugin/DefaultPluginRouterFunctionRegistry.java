package cn.tolim.pudans.plugin;

import cn.tolim.pudans.infra.PudansAttributes;
import cn.tolim.pudans.infra.SecureServerRequest;
import cn.tolim.pudans.infra.exception.PluginRuntimeIncompatibleException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
public class DefaultPluginRouterFunctionRegistry
        implements RouterFunction<ServerResponse>, PluginRouterFunctionRegistry {

    private final Collection<RouterFunction<ServerResponse>> routerFunctions;

    private final ConcurrentMap<RouterFunction<ServerResponse>, String> pluginIds;

    public DefaultPluginRouterFunctionRegistry() {
        this.routerFunctions = new CopyOnWriteArraySet<>();
        this.pluginIds = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        var secureRequest = new SecureServerRequest(request);
        return Flux.fromIterable(this.routerFunctions)
                .concatMap(routerFunction -> {
                    var pluginId = pluginIds.get(routerFunction);
                    return routerFunction
                            .route(secureRequest)
                            .map(hf -> (HandlerFunction<ServerResponse>) serverRequest -> {
                                if (pluginId != null) {
                                    secureRequest.attributes().put(PudansAttributes.PLUGIN_ID_ATTRIBUTE, pluginId);
                                }
                                try {
                                    return hf.handle(secureRequest);
                                } catch (LinkageError le) {
                                    return Mono.error(new PluginRuntimeIncompatibleException(le));
                                }
                            });
                })
                .next();
    }

    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        this.routerFunctions.forEach(routerFunction -> routerFunction.accept(visitor));
    }

    @Override
    public void register(String pluginId, Collection<RouterFunction<ServerResponse>> routerFunctions) {
        routerFunctions.forEach(routerFunction -> pluginIds.put(routerFunction, pluginId));
        this.routerFunctions.addAll(routerFunctions);
    }

    @Override
    public void register(Collection<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions.addAll(routerFunctions);
    }

    @Override
    public void unregister(Collection<RouterFunction<ServerResponse>> routerFunctions) {
        routerFunctions.forEach(pluginIds::remove);
        this.routerFunctions.removeAll(routerFunctions);
    }


    Collection<RouterFunction<ServerResponse>> getRouterFunctions() {
        return routerFunctions;
    }
}
