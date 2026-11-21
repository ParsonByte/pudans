package cn.tolim.pudans.core.ai.config.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.MetadataOperator;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiRouteGroupEndpoint implements CustomEndpoint {

    private static final String TAG = "AiRouteGroupV1alpha1";

    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/airoutegroups/-/arrange", this::arrange, builder -> builder
                        .operationId("arrangeAiRouteGroups")
                        .description("Write route group order according to the given names.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ArrangeRequest.class))
                        .response(responseBuilder()))
                .POST("/airoutegroups/{name}/actions/copy", this::copy, builder -> builder
                        .operationId("copyAiRouteGroup")
                        .description("Copy a route group with a new name.")
                        .tag(TAG)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.PATH)
                                .name("name")
                                .required(true)
                                .description("metadata.name of the route group."))
                        .requestBody(requestBodyBuilder().required(true).implementation(CopyRequest.class))
                        .response(responseBuilder().implementation(AiRouteGroup.class)))
                .build();
    }

    private Mono<ServerResponse> arrange(ServerRequest request) {
        return request
                .bodyToMono(ArrangeRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    var names = body.names() == null
                            ? List.<String>of()
                            : body.names().stream().filter(StringUtils::hasText).toList();
                    if (names.isEmpty() || new HashSet<>(names).size() != names.size()) {
                        return Mono.error(invalid("路由组名称列表不能为空且不得重复。"));
                    }
                    return Flux.fromIterable(names)
                            .index()
                            .concatMap(tuple -> client.get(AiRouteGroup.class, tuple.getT2())
                                    .switchIfEmpty(Mono.error(notFound("路由组不存在：" + tuple.getT2() + "。")))
                                    .flatMap(group -> {
                                        if (group.getSpec() == null) {
                                            group.setSpec(new AiRouteGroup.Spec());
                                        }
                                        group.getSpec().setOrder(tuple.getT1().intValue());
                                        return client.update(group);
                                    }))
                            .collectList()
                            .flatMap(groups -> ServerResponse.ok().bodyValue(groups));
                });
    }

    private Mono<ServerResponse> copy(ServerRequest request) {
        return request
                .bodyToMono(CopyRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    if (!StringUtils.hasText(body.name())) {
                        return Mono.error(invalid("新路由组名称不能为空。"));
                    }
                    return client.get(AiRouteGroup.class, request.pathVariable("name"))
                            .switchIfEmpty(Mono.error(notFound("路由组不存在。")))
                            .flatMap(source -> client.get(AiRouteGroup.class, body.name())
                                    .flatMap(existing -> Mono.<AiRouteGroup>error(conflict("路由组名称已存在。")))
                                    .switchIfEmpty(Mono.defer(() -> createCopy(source, body))))
                            .flatMap(group -> ServerResponse.ok().bodyValue(group));
                });
    }

    private Mono<AiRouteGroup> createCopy(AiRouteGroup source, CopyRequest request) {
        var copy = new AiRouteGroup();
        copy.setMetadata(copyMetadata(source.getMetadata(), request.name()));
        copy.setSpec(source.getSpec() == null
                ? new AiRouteGroup.Spec()
                : JsonUtils.deepCopy(source.getSpec()));
        copy.getSpec().setDisplayName(
                StringUtils.hasText(request.displayName())
                        ? request.displayName()
                        : source.getSpec() == null ? null : source.getSpec().getDisplayName());
        copy.getSpec().setDefault(false);
        copy.getSpec().setOrder(0);
        return client.create(copy);
    }

    private static Metadata copyMetadata(@Nullable MetadataOperator source, String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        if (source != null) {
            metadata.setLabels(source.getLabels() == null ? null : new HashMap<>(source.getLabels()));
            metadata.setAnnotations(
                    source.getAnnotations() == null ? null : new HashMap<>(source.getAnnotations()));
        }
        return metadata;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-route-group-request-invalid", message);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "ai-route-group-not-found", message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "ai-route-group-conflict", message);
    }

    public record ArrangeRequest(List<String> names) {}

    public record CopyRequest(String name, @Nullable String displayName) {}
}