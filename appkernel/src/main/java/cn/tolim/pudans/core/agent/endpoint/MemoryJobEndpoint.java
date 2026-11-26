package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.MemoryJob;
import cn.tolim.pudans.contract.agent.dto.MemoryJobCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryJobDetail;
import cn.tolim.pudans.contract.agent.dto.MemoryJobList;
import cn.tolim.pudans.core.agent.memory.MemoryAudit;
import cn.tolim.pudans.core.agent.memory.MemoryJobWorker;
import cn.tolim.pudans.core.agent.memory.MemoryJobs;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
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
public class MemoryJobEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryJobV1alpha1";

    private final MemoryJobs jobs;

    private final MemoryJobWorker worker;

    private final MemoryAudit audit;

    private final ReactiveRequestIdentity identity;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/memoryjobs", this::list, builder -> builder.operationId("listMemoryJobs")
                        .description("List personal and visible global memory jobs.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJobList.class)))
                .GET("/memoryjobs/-/mine", this::list, builder -> builder.operationId("listMyMemoryJobs")
                        .description("List personal and visible global memory jobs using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJobList.class)))
                .GET("/memoryjobs/-/all", this::all, builder -> builder.operationId("listAllMemoryJobs")
                        .description("List memory jobs across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJobList.class)))
                .POST("/memoryjobs", this::create, builder -> builder.operationId("createMemoryJob")
                        .description("Create an idempotent conversation memory extraction job.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryJobCreateRequest.class))))
                        .response(responseBuilder().implementation(MemoryJob.class)))
                .POST("/memoryjobs/-/run", this::create, builder -> builder.operationId("runMemoryJob")
                        .description("Run an idempotent memory extraction job using the contract path alias.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(MemoryJobCreateRequest.class))))
                        .response(responseBuilder().implementation(MemoryJob.class)))
                .GET("/memoryjobs/{name}", this::detail, builder -> builder.operationId("getMemoryJob")
                        .description("Get a memory job and its per-stage items.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJobDetail.class)))
                .GET("/memoryjobs/{name}/status", this::status, builder -> builder.operationId("getMemoryJobStatus")
                        .description("Get memory job status using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJob.class)))
                .GET("/memoryjobs/{name}/items", this::items, builder -> builder.operationId("listMemoryJobItems")
                        .description("List memory job items using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryJobDetail.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMapMany(owner -> jobs.list(
                        owner, MemoryEndpointSupport.limit(request, 20, 100)))
                .collectList()
                .map(MemoryJobList::new)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> all(ServerRequest request) {
        return jobs.listAll(MemoryEndpointSupport.limit(request, 20, 100))
                .collectList()
                .map(MemoryJobList::new)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request
                .bodyToMono(MemoryJobCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> jobs
                        .create(owner, body)
                        .flatMap(job -> audit.record(
                                        request.exchange(),
                                        owner,
                                        "MEMORY_JOB_CREATED",
                                        "MemoryJob",
                                        job.id(),
                                        true,
                                        Map.of(
                                                "jobType", job.jobType(),
                                                "status", job.status()))
                                .thenReturn(job))))
                .doOnNext(job -> worker.start(job.id()))
                .flatMap(job -> ServerResponse.status(HttpStatus.ACCEPTED).bodyValue(job));
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        return loadDetail(request).flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return loadDetail(request).flatMap(result -> ServerResponse.ok().bodyValue(result.job()));
    }

    private Mono<ServerResponse> items(ServerRequest request) {
        return loadDetail(request).flatMap(result -> ServerResponse.ok().bodyValue(result.items()));
    }

    private Mono<MemoryJobDetail> loadDetail(ServerRequest request) {
        var id = MemoryEndpointSupport.uuid(request.pathVariable("name"));
        return identity
                .resolve(request)
                .flatMap(owner -> jobs.detail(owner, id));
    }
}
