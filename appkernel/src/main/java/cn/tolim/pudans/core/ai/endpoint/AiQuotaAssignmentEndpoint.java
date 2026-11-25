package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiQuotaAssignment;
import cn.tolim.pudans.contract.ai.dto.AiQuotaAssignmentCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiQuotaAssignmentUpdateRequest;
import cn.tolim.pudans.core.ai.quota.AiQuotaAssignments;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiQuotaAssignmentEndpoint implements CustomEndpoint {

    private static final String TAG = "AiQuotaAssignmentV1alpha1";

    private final AiQuotaAssignments assignments;

    // 注册配额分配管理及主体解析路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiquotaassignments", this::list, builder -> builder.operationId("listAiQuotaAssignments")
                        .description("List AI quota assignments.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Quota assignments")))
                .POST("/aiquotaassignments", this::create, builder -> builder.operationId("createAiQuotaAssignment")
                        .description("Assign an AI quota plan to a user, workspace or role.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(AiQuotaAssignmentCreateRequest.class))))
                        .response(responseBuilder().implementation(AiQuotaAssignment.class)))
                .GET("/aiquotaassignments/-/resolve", this::resolve, builder -> builder.operationId("resolveAiQuotaAssignment")
                        .description("Resolve the active quota assignment for an explicit subject.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiQuotaAssignments.ResolvedQuota.class)))
                .GET("/aiquotaassignments/{name}", this::get, builder -> builder.operationId("getAiQuotaAssignment")
                        .description("Get an AI quota assignment.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiQuotaAssignment.class)))
                .PATCH("/aiquotaassignments/{name}", this::update, builder -> builder.operationId("updateAiQuotaAssignment")
                        .description("Update an AI quota assignment.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(AiQuotaAssignmentUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiQuotaAssignment.class)))
                .DELETE("/aiquotaassignments/{name}", this::delete, builder -> builder.operationId("deleteAiQuotaAssignment")
                        .description("Delete an AI quota assignment.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Assignment deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(assignments.list(), AiQuotaAssignment.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiQuotaAssignmentCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(assignments::create)
                .flatMap(assignment -> ServerResponse.status(HttpStatus.CREATED).bodyValue(assignment));
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        var subjectType = request.queryParam("subjectType")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-quota-assignment-subject-required",
                        "缺少配额主体类型。"));
        var subjectId = request.queryParam("subjectId")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-quota-assignment-subject-required",
                        "缺少配额主体。"));
        return assignments.resolve(subjectType, subjectId)
                .flatMap(resolved -> ServerResponse.ok().bodyValue(resolved));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return assignments
                .get(id(request))
                .flatMap(assignment -> ServerResponse.ok().bodyValue(assignment));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(AiQuotaAssignmentUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> assignments.update(id, body))
                .flatMap(assignment -> ServerResponse.ok().bodyValue(assignment));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return assignments.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"),
                "ai-quota-assignment-id-invalid",
                "配额分配标识不是有效 UUID。");
    }
}
