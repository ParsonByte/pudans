package cn.tolim.pudans.extension.router;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.extension.event.SchemeRemovedEvent;
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

    // 保存客户端与内建实体策略并初始化路由表。
    public ExtensionCompositeRouterFunction(
            ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
        schemeRouterFuncMapper = new ConcurrentHashMap<>();
    }

    // 依次匹配各 Scheme 路由并返回首个命中。
    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return Flux.fromIterable(getRouterFunctions())
                .concatMap(routerFunction -> routerFunction.route(request))
                .next();
    }

    // 把访问器转发给每个子路由函数。
    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        getRouterFunctions().forEach(routerFunction -> routerFunction.accept(visitor));
    }

    // 返回当前全部子路由的只读视图。
    private Iterable<RouterFunction<ServerResponse>> getRouterFunctions() {

        return Collections.unmodifiableCollection(schemeRouterFuncMapper.values());
    }

    // 为新增 Scheme 创建并注册路由。
    @EventListener
    void onSchemeAddedEvent(SchemeAddedEvent event) {
        var scheme = event.getScheme();
        var factory = new ExtensionRouterFunctionFactory(scheme, client, builtinEntityPolicy);
        this.schemeRouterFuncMapper.put(scheme, factory.create());
    }

    // 移除 Scheme 对应的路由。
    @EventListener
    void onSchemeRemovedEvent(SchemeRemovedEvent event) {
        this.schemeRouterFuncMapper.remove(event.getScheme());
    }
}
