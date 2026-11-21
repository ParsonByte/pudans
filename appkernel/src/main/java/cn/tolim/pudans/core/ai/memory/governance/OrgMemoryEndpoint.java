package cn.tolim.pudans.core.ai.memory.governance;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrgMemoryEndpoint implements CustomEndpoint {

    private static final String TAG = "OrgMemoryV1alpha1";

    private final OrgMemory service;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/orgmemories", this::list, builder -> builder
                        .operationId("listOrgMemories")
                        .description("List verified organization memories with their lineage.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(OrgMemoryRecord.class)))
                .GET("/orgmemories/-/pending", this::pending, builder -> builder
                        .operationId("listPendingOrgMemories")
                        .description("List organization memories waiting for review.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(OrgMemoryRecord.class)))
                .POST("/orgmemories/{id}/approve", this::approve, builder -> builder
                        .operationId("approveOrgMemory")
                        .description("Approve a pending organization memory.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(OrgMemoryRecord.class)))
                .POST("/orgmemories/{id}/reject", this::reject, builder -> builder
                        .operationId("rejectOrgMemory")
                        .description("Reject a pending organization memory.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(OrgMemoryRecord.class)))
                .POST("/orgmemories/{id}/revoke", this::revoke, builder -> builder
                        .operationId("revokeOrgMemory")
                        .description("Revoke a verified organization memory while keeping its lineage.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(OrgMemoryRecord.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return service
                .list(beforeId(request), limit(request))
                .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> pending(ServerRequest request) {
        return service
                .pending(beforeId(request), limit(request))
                .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> approve(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.approve(numericId(request.pathVariable("id")), actor))
                .flatMap(memory -> ServerResponse.ok().bodyValue(memory));
    }

    private Mono<ServerResponse> reject(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.reject(numericId(request.pathVariable("id")), actor))
                .flatMap(memory -> ServerResponse.ok().bodyValue(memory));
    }

    private Mono<ServerResponse> revoke(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.revoke(numericId(request.pathVariable("id")), actor))
                .flatMap(memory -> ServerResponse.ok().bodyValue(memory));
    }

    private static long beforeId(ServerRequest request) {
        return AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
    }

    private static int limit(ServerRequest request) {
        return AiEndpointRequests.intParam(request, "limit", 20, 1, 100);
    }

    private static long numericId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    MemoryGovernance.CODE_REQUEST_INVALID,
                    "组织记忆标识格式不正确。");
        }
    }
}
