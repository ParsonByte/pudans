package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.GetHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class ExtensionGetHandler implements GetHandler {
    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    // 保存方案与客户端。
    public ExtensionGetHandler(Scheme scheme, ReactiveExtensionClient client) {
        this.scheme = scheme;
        this.client = client;
    }

    // 返回单个资源的路径模板。
    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme) + "/{name}";
    }

    // 按名称读取扩展并返回 JSON。
    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        var extensionName = request.pathVariable("name");

        return client.get(scheme.type(), extensionName)
                .flatMap(extension -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(extension));
    }
}
