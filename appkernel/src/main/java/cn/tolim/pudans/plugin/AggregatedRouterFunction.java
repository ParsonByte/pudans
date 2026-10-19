package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.SecureServerRequest;
import cn.tolim.pudans.plugin.endpoint.CustomEndpointsBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


public class AggregatedRouterFunction implements RouterFunction<ServerResponse> {

    private final RouterFunction<ServerResponse> aggregated;

    public AggregatedRouterFunction(ObjectProvider<CustomEndpoint> customEndpoints) {
        var builder = new CustomEndpointsBuilder();
        customEndpoints.orderedStream().forEach(builder::add);
        this.aggregated = builder.build();
    }

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return aggregated.route(new SecureServerRequest(request));
    }

    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        this.aggregated.accept(visitor);
    }
}
