package cn.tolim.pudans.core.extension.router;

import static cn.tolim.pudans.core.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.extension.router.ExtensionRouterFunctionFactory.UpdateHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

class ExtensionUpdateHandler implements UpdateHandler {

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    ExtensionUpdateHandler(Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(Unstructured.class)
                .switchIfEmpty(Mono.error(
                        () -> new ServerWebInputException("无法将请求体读取为 " + scheme.groupVersionKind())))
                .flatMap(unstructured -> {
                    ExtensionIdentity.requireUnchanged(scheme, name, unstructured);
                    return builtinEntityPolicy
                            .checkUpdate(scheme.groupVersionKind(), name, unstructured)
                            .then(Mono.defer(() -> client.update(unstructured)));
                })
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme) + "/{name}";
    }
}
