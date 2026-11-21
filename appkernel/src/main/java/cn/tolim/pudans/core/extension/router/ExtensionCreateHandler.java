package cn.tolim.pudans.core.extension.router;

import static cn.tolim.pudans.core.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.extension.router.ExtensionRouterFunctionFactory.CreateHandler;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

class ExtensionCreateHandler implements CreateHandler {

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    ExtensionCreateHandler(Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        return request.bodyToMono(Unstructured.class)
                .switchIfEmpty(Mono.error(
                        () -> new ServerWebInputException("无法将请求体读取为 " + scheme.groupVersionKind())))
                .flatMap(unstructured -> {
                    ExtensionIdentity.requireGroupVersionKind(scheme, unstructured);
                    return builtinEntityPolicy
                            .checkCreate(scheme.groupVersionKind(), unstructured)
                            .then(Mono.defer(() -> client.create(unstructured)));
                })
                .flatMap(createdExt -> ServerResponse.created(URI.create(
                                pathPattern() + "/" + createdExt.getMetadata().getName()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(createdExt));
    }

    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme);
    }
}
