package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.CreateHandler;
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

    // 保存方案、客户端与内建实体策略。
    ExtensionCreateHandler(Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    // 校验资源标识与创建权限后写入、回读并返回 201。
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
                .flatMap(createdExt -> client.get(scheme.type(), createdExt.getMetadata().getName()))
                .flatMap(createdExt -> ServerResponse.created(URI.create(
                                pathPattern() + "/" + createdExt.getMetadata().getName()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(createdExt));
    }

    // 返回集合资源的路径模板。
    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme);
    }
}
