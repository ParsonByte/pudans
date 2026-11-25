package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.dto.MemoryRetrievalResult;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrieveRequest;
import cn.tolim.pudans.contract.agent.dto.UnderstandingMap;
import cn.tolim.pudans.contract.agent.dto.UnderstandingStatus;
import cn.tolim.pudans.core.agent.memory.MemoryRetrieval;
import cn.tolim.pudans.core.agent.memory.UnderstandingMaps;
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
public class UnderstandingEndpoint implements CustomEndpoint {

    private static final String TAG = "UnderstandingV1alpha1";

    private final UnderstandingMaps maps;

    private final MemoryRetrieval retrieval;

    private final ReactiveRequestIdentity identity;

    // 注册真实认知地图、引擎状态和记忆检索路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/understanding/-/map", this::map, builder -> builder
                        .operationId("getUnderstandingMap")
                        .description("Get real memory counts, confidence and updates by dimension.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(UnderstandingMap.class)))
                .GET("/understanding/-/status", this::status, builder -> builder
                        .operationId("getUnderstandingStatus")
                        .description("Get extraction and vector configuration status.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(UnderstandingStatus.class)))
                .POST("/understanding/-/query", this::query, builder -> builder
                        .operationId("queryUnderstandingMemories")
                        .description("Retrieve published global and active personal memories.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryRetrieveRequest.class))))
                        .response(responseBuilder().implementation(MemoryRetrievalResult.class)))
                .build();
    }

    private Mono<ServerResponse> map(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(maps::map)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(maps::status)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> query(ServerRequest request) {
        return request
                .bodyToMono(MemoryRetrieveRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> retrieval
                        .retrieve(owner, body)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
}
