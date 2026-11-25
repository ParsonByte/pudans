package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.MemoryVectorSpace;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceStatus;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceSyncResult;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceValidationResult;
import cn.tolim.pudans.core.agent.memory.MemoryVectorSpaces;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
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
public class MemoryVectorSpaceEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryVectorSpaceV1alpha1";

    private final MemoryVectorSpaces spaces;

    private final ReactiveRequestIdentity identity;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/memoryvectorspaces", this::list, builder -> builder.operationId("listMemoryVectorSpaces")
                        .description("List registered memory vector spaces.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpace.class)))
                .POST("/memoryvectorspaces", this::create, builder -> builder.operationId("createMemoryVectorSpace")
                        .description("Register one physically validated embedding vector space.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryVectorSpaceCreateRequest.class))))
                        .response(responseBuilder().implementation(MemoryVectorSpace.class)))
                .POST("/memoryvectorspaces/-/validate", this::validate, builder -> builder
                        .operationId("validateMemoryVectorSpace")
                        .description("Validate an embedding model, vector table and HNSW index without writing.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryVectorSpaceCreateRequest.class))))
                        .response(responseBuilder().implementation(MemoryVectorSpaceValidationResult.class)))
                .POST("/memoryvectorspaces/-/sync", this::sync, builder -> builder.operationId("syncMemoryVectorSpaces")
                        .description("Validate vector spaces against pgvector, active models and physical tables.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpaceSyncResult.class)))
                .GET("/memoryvectorspaces/{name}", this::view, builder -> builder.operationId("getMemoryVectorSpace")
                        .description("Get a registered memory vector space by model key.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpace.class)))
                .GET("/memoryvectorspaces/{name}/status", this::status, builder -> builder
                        .operationId("getMemoryVectorSpaceStatus")
                        .description("Probe a registered vector space and return its readiness.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpaceStatus.class)))
                .POST("/memoryvectorspaces/{name}/-/enable", this::enable, builder -> builder
                        .operationId("enableMemoryVectorSpace")
                        .description("Validate and enable a registered memory vector space.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpace.class)))
                .POST("/memoryvectorspaces/{name}/-/disable", this::disable, builder -> builder
                        .operationId("disableMemoryVectorSpace")
                        .description("Disable a memory vector space without deleting its registration.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryVectorSpace.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMapMany(owner -> spaces.list())
                .collectList()
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request
                .bodyToMono(MemoryVectorSpaceCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> spaces
                        .create(owner.workspaceId(), body)))
                .flatMap(result -> ServerResponse.status(HttpStatus.CREATED).bodyValue(result));
    }

    private Mono<ServerResponse> validate(ServerRequest request) {
        return request
                .bodyToMono(MemoryVectorSpaceCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> spaces
                        .validate(owner.workspaceId(), body)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> sync(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> spaces.sync())
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> view(ServerRequest request) {
        return spaces
                .find(request.pathVariable("name"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> spaces.status(owner.workspaceId(), request.pathVariable("name")))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> enable(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> spaces.enable(owner.workspaceId(), request.pathVariable("name")))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> disable(ServerRequest request) {
        return spaces.disable(request.pathVariable("name"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
}
