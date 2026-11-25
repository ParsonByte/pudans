package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelUpdateRequest;
import cn.tolim.pudans.core.ai.AiChannelModels;
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
public class AiChannelModelEndpoint implements CustomEndpoint {

    private static final String TAG = "AiChannelModelV1alpha1";

    private final AiChannelModels channelModels;

    // 注册渠道与逻辑模型映射管理路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aichannelmodels", this::list, builder -> builder.operationId("listAiChannelModels")
                        .description("List AI channel to model mappings.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("AI channel models")))
                .POST("/aichannelmodels", this::create, builder -> builder.operationId("createAiChannelModel")
                        .description("Create an AI channel to model mapping.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiChannelModelCreateRequest.class))))
                        .response(responseBuilder().implementation(AiChannelModel.class)))
                .GET("/aichannelmodels/{name}", this::get, builder -> builder.operationId("getAiChannelModel")
                        .description("Get an AI channel to model mapping.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiChannelModel.class)))
                .PATCH("/aichannelmodels/{name}", this::update, builder -> builder.operationId("updateAiChannelModel")
                        .description("Update an AI channel to model mapping.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiChannelModelUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiChannelModel.class)))
                .DELETE("/aichannelmodels/{name}", this::delete, builder -> builder.operationId("deleteAiChannelModel")
                        .description("Delete an AI channel to model mapping.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Mapping deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(channelModels.list(), AiChannelModel.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiChannelModelCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(channelModels::create)
                .flatMap(model -> ServerResponse.status(HttpStatus.CREATED).bodyValue(model));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return channelModels.get(id(request)).flatMap(model -> ServerResponse.ok().bodyValue(model));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(AiChannelModelUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> channelModels.update(id, body))
                .flatMap(model -> ServerResponse.ok().bodyValue(model));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return channelModels.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"),
                "ai-channel-model-id-invalid",
                "渠道路由标识不是有效 UUID。");
    }
}
