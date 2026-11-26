package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiDefaultModel;
import cn.tolim.pudans.contract.ai.dto.AiDefaultModelRequest;
import cn.tolim.pudans.core.ai.AiDefaultModels;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiDefaultModelEndpoint implements CustomEndpoint {

    private static final String TAG = "AiDefaultModelV1alpha1";

    private final AiDefaultModels defaults;

    private final ReactiveRequestIdentity identity;

    // 注册工作区默认模型列表与按能力替换路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aidefaultmodels", this::list, builder -> builder.operationId("listAiDefaultModels")
                        .description("List default models configured for the current workspace.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").implementationArray(AiDefaultModel.class)))
                .GET("/aidefaultmodels/-/all", this::all, builder -> builder.operationId("listAllAiDefaultModels")
                        .description("List default models across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").implementationArray(AiDefaultModel.class)))
                .PUT("/aidefaultmodels/{capability}", this::put, builder -> builder.operationId("putAiDefaultModel")
                        .description("Set the default model for a capability in the current workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiDefaultModelRequest.class))))
                        .response(responseBuilder().implementation(AiDefaultModel.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return identity.resolve(request).flatMap(owner -> ServerResponse.ok()
                .body(defaults.list(owner.workspaceId()), AiDefaultModel.class));
    }

    private Mono<ServerResponse> all(ServerRequest request) {
        return ServerResponse.ok().body(defaults.listAll(), AiDefaultModel.class);
    }

    private Mono<ServerResponse> put(ServerRequest request) {
        var capability = request.pathVariable("capability");
        return request.bodyToMono(AiDefaultModelRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request)
                        .flatMap(owner -> defaults.put(owner.workspaceId(), capability, body)))
                .flatMap(model -> ServerResponse.ok().bodyValue(model));
    }
}
