package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.MemoryItem;
import cn.tolim.pudans.contract.agent.dto.MemoryCorrectionRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrievalResult;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrieveRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryUpdateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryWithdrawRequest;
import cn.tolim.pudans.core.agent.memory.MemoryAudit;
import cn.tolim.pudans.core.agent.memory.MemoryPoolReviews;
import cn.tolim.pudans.core.agent.memory.MemoryRetrieval;
import cn.tolim.pudans.core.agent.memory.PersonalMemories;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.Map;
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
public class MemoryEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryV1alpha1";

    private final PersonalMemories memories;

    private final MemoryRetrieval retrieval;

    private final MemoryPoolReviews poolReviews;

    private final MemoryAudit audit;

    private final ReactiveRequestIdentity identity;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/memories", this::list, builder -> builder.operationId("listPersonalMemories")
                        .description("List personal memories owned by the current user and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Personal memories")))
                .GET("/memories/-/personal", this::list, builder -> builder.operationId("listPersonalMemoriesAlias")
                        .description("List personal memories using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Personal memories")))
                .POST("/memories", this::create, builder -> builder.operationId("createPersonalMemory")
                        .description("Create an L1-L3 personal memory for the current user and workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryCreateRequest.class))))
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .GET("/memories/{name}", this::view, builder -> builder.operationId("getPersonalMemory")
                        .description("Get one personal memory.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .GET("/memories/{name}/view", this::view, builder -> builder.operationId("getPersonalMemoryAlias")
                        .description("Get one personal memory using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .PATCH("/memories/{name}", this::update, builder -> builder.operationId("updatePersonalMemory")
                        .description("Update summary, type, confidence or validity without rewriting content.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryUpdateRequest.class))))
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .DELETE("/memories/{name}", this::withdraw, builder -> builder.operationId("withdrawPersonalMemory")
                        .description("Withdraw a personal memory without physically deleting its evidence chain.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Memory withdrawn")))
                .POST(
                        "/memories/{name}/-/correct",
                        this::correct,
                        builder -> builder.operationId("correctPersonalMemory")
                        .description("Create a corrected version and supersede the previous memory.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryCorrectionRequest.class))))
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .POST(
                        "/memories/{name}/-/withdraw",
                        this::withdrawWithReason,
                        builder -> builder.operationId("withdrawPersonalMemoryWithReason")
                        .description("Withdraw a personal memory, or a pool item with admin ACL.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryItem.class)))
                .POST("/memories/-/retrieve", this::retrieve, builder -> builder.operationId("retrieveMemories")
                        .description("Retrieve published global memory first, then active personal memory.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryRetrieveRequest.class))))
                        .response(responseBuilder().implementation(MemoryRetrievalResult.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var layer = request.queryParam("layer").orElse(null);
        var status = request.queryParam("status").orElse(null);
        return identity
                .resolve(request)
                .flatMapMany(owner -> memories.list(
                        owner, layer, status, MemoryEndpointSupport.limit(request, 20, 100)))
                .collectList()
                .flatMap(items -> ServerResponse.ok().bodyValue(Map.of("items", items)));
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request
                .bodyToMono(MemoryCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> memories
                        .create(owner, body)
                        .flatMap(created -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_CREATED",
                                        "MemoryItem",
                                        created.id(),
                                        true,
                                        context(created))
                                .thenReturn(created))))
                .flatMap(created -> ServerResponse.status(HttpStatus.CREATED).bodyValue(created));
    }

    private Mono<ServerResponse> view(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return identity
                .resolve(request)
                .flatMap(owner -> memories.view(owner, id))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return request
                .bodyToMono(MemoryUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> memories
                        .update(owner, id, body)
                        .flatMap(updated -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_UPDATED",
                                        "MemoryItem",
                                        updated.id(),
                                        true,
                                        context(updated))
                                .thenReturn(updated))))
                .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> correct(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return request
                .bodyToMono(MemoryCorrectionRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> memories
                        .correct(owner, id, body)
                        .flatMap(corrected -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_CORRECTED",
                                        "MemoryItem",
                                        corrected.id(),
                                        true,
                                        context(corrected))
                                .thenReturn(corrected))))
                .flatMap(corrected -> ServerResponse.ok().bodyValue(corrected));
    }

    private Mono<ServerResponse> withdraw(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return identity
                .resolve(request)
                .flatMap(owner -> memories.withdraw(owner, id, null))
                .flatMap(withdrawn -> audit.record(
                                request.exchange(),
                                new RequestIdentity(
                                        withdrawn.ownerId(), withdrawn.workspaceId()),
                                "MEMORY_WITHDRAWN",
                                "MemoryItem",
                                withdrawn.id(),
                                true,
                                context(withdrawn))
                        .then(ServerResponse.noContent().build()));
    }

    private Mono<ServerResponse> withdrawWithReason(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return request
                .bodyToMono(MemoryWithdrawRequest.class)
                .defaultIfEmpty(new MemoryWithdrawRequest(null))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> memories
                        .withdraw(owner, id, body)
                        .flatMap(withdrawn -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_WITHDRAWN",
                                        "MemoryItem",
                                        withdrawn.id(),
                                        true,
                                        context(withdrawn))
                                .thenReturn(withdrawn))
                        .flatMap(withdrawn -> ServerResponse.ok().bodyValue(withdrawn))
                        .onErrorResume(
                                MemoryEndpoint::personalMemoryMissing,
                                error -> identity
                                        .roles()
                                        .flatMap(roles -> poolReviews.withdraw(
                                                owner, roles, id))
                                        .flatMap(withdrawn -> audit.record(
                                                        request.exchange(),
                                                        owner,
                                                        "MEMORY_POOL_WITHDRAWN",
                                                        "MemoryPoolItem",
                                                        withdrawn.id(),
                                                        true,
                                                        Map.of("status", withdrawn.status()))
                                                .thenReturn(withdrawn))
                                        .flatMap(withdrawn -> ServerResponse.ok()
                                                .bodyValue(withdrawn)))));
    }

    private Mono<ServerResponse> retrieve(ServerRequest request) {
        return request
                .bodyToMono(MemoryRetrieveRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> retrieval
                        .retrieve(owner, body)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static Map<String, Object> context(MemoryItem item) {
        return Map.of(
                "layer", item.layer(),
                "memoryType", item.memoryType(),
                "status", item.status());
    }

    private static boolean personalMemoryMissing(Throwable error) {
        return error instanceof ApiException api
                && "memory-not-found".equals(api.getCode());
    }
}
