package cn.tolim.pudans.extension.router;

import static cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PathPatternGenerator.buildExtensionPathPattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.extension.contract.JsonExtension;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.router.ExtensionRouterFunctionFactory.PatchHandler;

@Slf4j
public class ExtensionPatchHandler implements PatchHandler {

    private static final MediaType JSON_PATCH_MEDIA_TYPE = MediaType.valueOf("application/json-patch+json");

    private final Scheme scheme;

    private final ReactiveExtensionClient client;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    public ExtensionPatchHandler(
            Scheme scheme, ReactiveExtensionClient client, BuiltinEntityPolicy builtinEntityPolicy) {
        this.scheme = scheme;
        this.client = client;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        var name = request.pathVariable("name");
        var contentTypeOpt = request.headers().contentType();
        if (contentTypeOpt.isEmpty()) {
            return Mono.error(
                    new UnsupportedMediaTypeStatusException((MediaType) null, List.of(JSON_PATCH_MEDIA_TYPE)));
        }
        var contentType = contentTypeOpt.get();
        if (!contentType.isCompatibleWith(JSON_PATCH_MEDIA_TYPE)) {
            return Mono.error(new UnsupportedMediaTypeStatusException(contentType, List.of(JSON_PATCH_MEDIA_TYPE)));
        }

        return request.bodyToMono(JsonPatch.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("请求体不能为空。")))
                .flatMap(jsonPatch -> client.getJsonExtension(scheme.groupVersionKind(), name)
                        .flatMap(jsonExtension -> {
                            try {
                                
                                var appliedJsonNode = jsonPatch.apply(jsonExtension.getInternal());
                                if (!(appliedJsonNode instanceof ObjectNode objectNode)) {
                                    throw new ServerWebInputException("打补丁后的扩展必须是一个对象。");
                                }
                                var patchedExtension = new JsonExtension(jsonExtension.getObjectMapper(), objectNode);
                                ExtensionIdentity.requireUnchanged(scheme, name, patchedExtension);
                                return builtinEntityPolicy
                                        .checkUpdate(scheme.groupVersionKind(), name, patchedExtension)
                                        .then(Mono.defer(() -> client.update(patchedExtension)));
                            } catch (JsonPatchException e) {
                                return Mono.error(new ServerWebInputException(
                                        "无法应用 JSON Patch。", null, e));
                            }
                        }))
                .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    @Override
    public String pathPattern() {
        return buildExtensionPathPattern(scheme) + "/{name}";
    }
}
