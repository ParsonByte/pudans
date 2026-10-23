package cn.tolim.pudans.plugin;

import java.util.Collection;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

public interface PluginRouterFunctionRegistry {
    default void register(String pluginId, Collection<RouterFunction<ServerResponse>> routerFunctions) {
        register(routerFunctions);
    }

    void register(Collection<RouterFunction<ServerResponse>> routerFunctions);

    void unregister(Collection<RouterFunction<ServerResponse>> routerFunctions);
}
