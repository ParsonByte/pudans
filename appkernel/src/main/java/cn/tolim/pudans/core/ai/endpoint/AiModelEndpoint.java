package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.dto.AiModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelUpdateRequest;
import cn.tolim.pudans.core.ai.AiModels;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelEndpoint implements CustomEndpoint {

    private static final String TAG = "AiModelV1alpha1";

    private final AiModels models;

    // 注册逻辑模型管理路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aimodels", this::list, builder -> builder.operationId("listAiModels")
                        .description("List AI logical models and their parameter schemas.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("AI models")))
                .POST("/aimodels", this::create, builder -> builder.operationId("createAiModel")
                        .description("Create an AI logical model.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiModelCreateRequest.class))))
                        .response(responseBuilder().implementation(AiModel.class)))
                .GET("/aimodels/{name}", this::get, builder -> builder.operationId("getAiModel")
                        .description("Get an AI logical model.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiModel.class)))
                .PATCH("/aimodels/{name}", this::update, builder -> builder.operationId("updateAiModel")
                        .description("Update an AI logical model.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiModelUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiModel.class)))
                .DELETE("/aimodels/{name}", this::delete, builder -> builder.operationId("deleteAiModel")
                        .description("Delete an AI model that is not referenced by configuration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("AI model deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(models.list(), AiModel.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiModelCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(models::create)
                .flatMap(model -> ServerResponse.status(HttpStatus.CREATED).bodyValue(model));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return models.get(id(request)).flatMap(model -> ServerResponse.ok().bodyValue(model));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(AiModelUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> models.update(id, body))
                .flatMap(model -> ServerResponse.ok().bodyValue(model));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return models.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-model-id-invalid", "模型标识不是有效 UUID。");
    }
}
