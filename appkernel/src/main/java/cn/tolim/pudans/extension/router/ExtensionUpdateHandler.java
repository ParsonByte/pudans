package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.UpdateHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

class ExtensionUpdateHandler implements UpdateHandler {

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    // 保存方案、客户端与内建实体策略。
    ExtensionUpdateHandler(Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    // 校验标识与更新权限后写入并回读结果。
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
                            .then(Mono.defer(() -> client.update(unstructured)))
                            .then(client.get(scheme.type(), name));
                })
                // 返回强类型对象，让响应序列化器应用敏感字段脱敏规则。
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    // 返回单个资源的路径模板。
    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme) + "/{name}";
    }
}
