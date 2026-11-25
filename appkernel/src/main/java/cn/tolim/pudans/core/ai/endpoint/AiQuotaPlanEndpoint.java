package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanUpdateRequest;
import cn.tolim.pudans.core.ai.quota.AiQuotaPlans;
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
public class AiQuotaPlanEndpoint implements CustomEndpoint {

    private static final String TAG = "AiQuotaPlanV1alpha1";

    private final AiQuotaPlans plans;

    // 注册配额套餐管理路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiquotaplans", this::list, builder -> builder.operationId("listAiQuotaPlans")
                        .description("List AI quota plans.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Quota plans")))
                .POST("/aiquotaplans", this::create, builder -> builder.operationId("createAiQuotaPlan")
                        .description("Create an AI quota plan with fixed five-hour and weekly windows.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiQuotaPlanCreateRequest.class))))
                        .response(responseBuilder().implementation(AiQuotaPlan.class)))
                .GET("/aiquotaplans/{name}", this::get, builder -> builder.operationId("getAiQuotaPlan")
                        .description("Get an AI quota plan.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiQuotaPlan.class)))
                .PATCH("/aiquotaplans/{name}", this::update, builder -> builder.operationId("updateAiQuotaPlan")
                        .description("Update an AI quota plan.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiQuotaPlanUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiQuotaPlan.class)))
                .DELETE("/aiquotaplans/{name}", this::delete, builder -> builder.operationId("deleteAiQuotaPlan")
                        .description("Delete a quota plan that is not assigned or reserved.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Quota plan deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(plans.list(), AiQuotaPlan.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiQuotaPlanCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(plans::create)
                .flatMap(plan -> ServerResponse.status(HttpStatus.CREATED).bodyValue(plan));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return plans.get(id(request)).flatMap(plan -> ServerResponse.ok().bodyValue(plan));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(AiQuotaPlanUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> plans.update(id, body))
                .flatMap(plan -> ServerResponse.ok().bodyValue(plan));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return plans.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"),
                "ai-quota-plan-id-invalid",
                "配额套餐标识不是有效 UUID。");
    }
}
