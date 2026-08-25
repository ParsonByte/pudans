package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.contract.extension.router.SortableRequest;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.ListHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class ExtensionListHandler implements ListHandler {
    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    public ExtensionListHandler(Scheme scheme, ReactiveExtensionClient client) {
        this.scheme = scheme;
        this.client = client;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        var queryParams = new SortableRequest(request.exchange());
        return client.listBy(scheme.type(), queryParams.toListOptions(), queryParams.toPageRequest())
                .flatMap(listResult -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(listResult));
    }

    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme);
    }
}
