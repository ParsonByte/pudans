package cn.tolim.pudans.core.extension.router;

import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.core.extension.event.SchemeRemovedEvent;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ExtensionCompositeRouterFunction implements RouterFunction<ServerResponse> {

    private final ConcurrentMap<Scheme, RouterFunction<ServerResponse>> schemeRouterFuncMapper;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    public ExtensionCompositeRouterFunction(
            ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
        schemeRouterFuncMapper = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return Flux.fromIterable(getRouterFunctions())
                .concatMap(routerFunction -> routerFunction.route(request))
                .next();
    }

    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        getRouterFunctions().forEach(routerFunction -> routerFunction.accept(visitor));
    }

    private Iterable<RouterFunction<ServerResponse>> getRouterFunctions() {

        return Collections.unmodifiableCollection(schemeRouterFuncMapper.values());
    }

    @EventListener
    void onSchemeAddedEvent(SchemeAddedEvent event) {
        var scheme = event.getScheme();
        var factory = new ExtensionRouterFunctionFactory(scheme, client, builtinEntityPolicy);
        this.schemeRouterFuncMapper.put(scheme, factory.create());
    }

    @EventListener
    void onSchemeRemovedEvent(SchemeRemovedEvent event) {
        this.schemeRouterFuncMapper.remove(event.getScheme());
    }
}
