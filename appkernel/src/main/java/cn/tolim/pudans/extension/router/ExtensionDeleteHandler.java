package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.DeleteHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class ExtensionDeleteHandler implements DeleteHandler {

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    ExtensionDeleteHandler(Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.get(scheme.type(), name)
                .flatMap(extension -> builtinEntityPolicy.checkDelete(extension).thenReturn(extension))
                .flatMap(client::delete)
                .flatMap(deleted -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(deleted));
    }

    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme) + "/{name}";
    }
}
