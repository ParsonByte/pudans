package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.MemoryPoolItem;
import cn.tolim.pudans.contract.agent.dto.MemoryPoolList;
import cn.tolim.pudans.contract.agent.dto.MemoryPoolReviewRequest;
import cn.tolim.pudans.core.agent.memory.MemoryAudit;
import cn.tolim.pudans.core.agent.memory.MemoryPoolReviews;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryPoolEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryPoolV1alpha1";

    private final MemoryPoolReviews reviews;

    private final MemoryAudit audit;

    private final ReactiveRequestIdentity identity;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/memories/-/pool", this::list, builder -> builder.operationId("listMemoryPool")
                        .description("List global memory pool entries. Non-published statuses require review ACL.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryPoolList.class)))
                .POST(
                        "/memories/{name}/-/approve",
                        this::approve,
                        builder -> builder.operationId("approveMemoryPoolItem")
                        .description("Approve a candidate from at least two owners and two source workspaces.")
                        .tag(TAG)
                        .requestBody(reviewBody())
                        .response(responseBuilder().implementation(MemoryPoolItem.class)))
                .POST(
                        "/memories/{name}/-/publish",
                        this::publish,
                        builder -> builder.operationId("publishMemoryPoolItem")
                        .description("Publish an approved general memory pool item.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryPoolItem.class)))
                .POST("/memories/{name}/-/reject", this::reject, builder -> builder.operationId("rejectMemoryPoolItem")
                        .description("Reject a candidate or approved memory pool item.")
                        .tag(TAG)
                        .requestBody(reviewBody())
                        .response(responseBuilder().implementation(MemoryPoolItem.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var status = request.queryParam("status").orElse("published");
        var limit = MemoryEndpointSupport.limit(request, 20, 100);
        return identity
                .resolve(request)
                .flatMap(owner -> identity
                        .roles()
                        .flatMapMany(roles -> reviews.list(owner, roles, status, limit))
                        .collectList()
                        .map(MemoryPoolList::new))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> approve(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return request
                .bodyToMono(MemoryPoolReviewRequest.class)
                .defaultIfEmpty(new MemoryPoolReviewRequest(null, null, null))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> identity
                        .roles()
                        .flatMap(roles -> reviews.approve(owner, roles, id, body))
                        .flatMap(item -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_POOL_APPROVED",
                                        "MemoryPoolItem",
                                        item.id(),
                                        true,
                                        Map.of("status", item.status()))
                                .thenReturn(item))))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<ServerResponse> publish(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return identity
                .resolve(request)
                .flatMap(owner -> identity
                        .roles()
                        .flatMap(roles -> reviews.publish(owner, roles, id))
                        .flatMap(item -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_POOL_PUBLISHED",
                                        "MemoryPoolItem",
                                        item.id(),
                                        true,
                                        Map.of("status", item.status()))
                                .thenReturn(item)))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<ServerResponse> reject(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return request
                .bodyToMono(MemoryPoolReviewRequest.class)
                .defaultIfEmpty(new MemoryPoolReviewRequest(null, null, null))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> identity
                        .roles()
                        .flatMap(roles -> reviews.reject(owner, roles, id, body))
                        .flatMap(item -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_POOL_REJECTED",
                                        "MemoryPoolItem",
                                        item.id(),
                                        true,
                                        Map.of("status", item.status()))
                                .thenReturn(item))))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder reviewBody() {
        return requestBodyBuilder()
                .content(contentBuilder()
                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(MemoryPoolReviewRequest.class)));
    }
}
