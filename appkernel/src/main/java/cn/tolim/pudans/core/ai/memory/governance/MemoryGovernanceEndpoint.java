package cn.tolim.pudans.core.ai.memory.governance;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.MemoryRequestStatus;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.MemoryRequestType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.PipelineStats;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.RebuildResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryRequestRecord;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryGovernanceEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryGovernanceV1alpha1";

    private static final String CODE_REQUEST_INVALID = MemoryGovernance.CODE_REQUEST_INVALID;

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv");

    private static final String CSV_CONTENT_DISPOSITION = "attachment; filename=\"memory-records.csv\"";

    private final MemoryGovernance service;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/memories/{name}/correction-request", this::createCorrectionRequest, builder -> builder
                        .operationId("createMemoryCorrectionRequest")
                        .description("Submit a correction or deletion request for an own L1 memory.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .implementation(CorrectionRequest.class))
                        .response(responseBuilder().implementation(MemoryRequestRecord.class)))
                .GET("/memories/-/requests", this::myRequests, builder -> builder
                        .operationId("listMyMemoryRequests")
                        .description("List the current user's memory correction requests.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(MemoryRequestRecord.class)))
                .GET("/memoryrecords", this::listRecords, builder -> builder
                        .operationId("listMemoryRecords")
                        .description("List L1 memories across users with governance filters.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("teamId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agentId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("userId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("type").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(L1MemoryRecord.class)))
                .GET("/memoryrecords/-/pipeline", this::pipeline, builder -> builder
                        .operationId("getMemoryPipeline")
                        .description("Get memory pipeline task and conversation backlog counters.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PipelineStats.class)))
                .POST("/memoryrecords/-/flush", this::flush, builder -> builder
                        .operationId("flushMemoryPipeline")
                        .description("Manually enqueue L1 extraction for a conversation.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(FlushRequest.class))
                        .response(responseBuilder().responseCode("202")))
                .POST("/memoryrecords/-/rebuild", this::rebuild, builder -> builder
                        .operationId("rebuildMemoryRecords")
                        .description("Enqueue an L1, L2 or L3 memory rebuild for a conversation.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(RebuildRequest.class))
                        .response(responseBuilder().responseCode("202").implementation(RebuildResult.class)))
                .GET("/memoryrecords/-/export", this::export, builder -> builder
                        .operationId("exportMemoryRecords")
                        .description("Export filtered L1 memories as a CSV attachment.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("teamId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agentId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("userId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("type").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("from").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("to").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().responseCode("200").description("Memory records CSV")))
                .GET("/memoryrecords/{name}", this::detail, builder -> builder
                        .operationId("getMemoryRecord")
                        .description("Get one L1 memory by 'L1:<id>' or numeric id.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .POST("/memoryrecords/{name}/freeze", this::freeze, builder -> builder
                        .operationId("freezeMemoryRecord")
                        .description("Freeze an active L1 memory.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .POST("/memoryrecords/{name}/unfreeze", this::unfreeze, builder -> builder
                        .operationId("unfreezeMemoryRecord")
                        .description("Unfreeze a frozen L1 memory.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .DELETE("/memoryrecords/{name}", this::deleteRecord, builder -> builder
                        .operationId("deleteMemoryRecord")
                        .description("Logically delete an L1 memory with a required reason and snapshot.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("reason").required(true))
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .POST("/memoryrecords/{name}/restore", this::restore, builder -> builder
                        .operationId("restoreMemoryRecord")
                        .description("Restore an L1 memory from its latest snapshot.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .GET("/memoryrequests", this::listRequests, builder -> builder
                        .operationId("listMemoryRequests")
                        .description("List memory correction requests by status.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(MemoryRequestRecord.class)))
                .POST("/memoryrequests/{id}/approve", this::approve, builder -> builder
                        .operationId("approveMemoryRequest")
                        .description("Approve a pending memory correction request.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(false).implementation(ReviewRequest.class))
                        .response(responseBuilder().implementation(MemoryRequestRecord.class)))
                .POST("/memoryrequests/{id}/reject", this::reject, builder -> builder
                        .operationId("rejectMemoryRequest")
                        .description("Reject a pending memory correction request with a comment.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ReviewRequest.class))
                        .response(responseBuilder().implementation(MemoryRequestRecord.class)))
                .build();
    }

    private Mono<ServerResponse> createCorrectionRequest(ServerRequest request) {
        return request.bodyToMono(CorrectionRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> service.createRequest(
                        userId,
                        request.pathVariable("name"),
                        requestType(body.requestType()),
                        body.reason(),
                        body.suggestedContent())))
                .flatMap(created -> ServerResponse.ok().bodyValue(created));
    }

    private Mono<ServerResponse> myRequests(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(userId -> service
                        .listRequests(
                                userId,
                                requestStatus(request),
                                beforeId(request),
                                limit(request))
                        .collectList()
                        .flatMap(items -> ServerResponse.ok().bodyValue(items)));
    }

    private Mono<ServerResponse> listRecords(ServerRequest request) {
        return service
                .list(
                        query(request, "teamId"),
                        query(request, "agentId"),
                        query(request, "userId"),
                        type(request),
                        memoryStatus(request),
                        beforeId(request),
                        limit(request))
                .flatMap(records -> ServerResponse.ok().bodyValue(records));
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        return service
                .get(MemoryGovernance.parseId(request.pathVariable("name")))
                .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> freeze(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.freeze(
                        MemoryGovernance.parseId(request.pathVariable("name")), actor))
                .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> unfreeze(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.unfreeze(
                        MemoryGovernance.parseId(request.pathVariable("name")), actor))
                .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> deleteRecord(ServerRequest request) {
        var reason = query(request, "reason");
        return requests
                .userId(request)
                .flatMap(actor -> service.delete(
                        MemoryGovernance.parseId(request.pathVariable("name")), reason, actor))
                .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> restore(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(actor -> service.restore(
                        MemoryGovernance.parseId(request.pathVariable("name")), actor))
                .flatMap(record -> ServerResponse.ok().bodyValue(record));
    }

    private Mono<ServerResponse> listRequests(ServerRequest request) {
        return service
                .listRequests(null, requestStatus(request), beforeId(request), limit(request))
                .collectList()
                .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> approve(ServerRequest request) {
        return request.bodyToMono(ReviewRequest.class)
                .defaultIfEmpty(new ReviewRequest(null))
                .flatMap(body -> requests.userId(request).flatMap(actor -> service.approve(
                        numericId(request.pathVariable("id")), actor, body.comment())))
                .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> reject(ServerRequest request) {
        return request.bodyToMono(ReviewRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    if (!StringUtils.hasText(body.comment())) {
                        return Mono.error(invalid("驳回意见不能为空。"));
                    }
                    return requests.userId(request).flatMap(actor -> service.reject(
                            numericId(request.pathVariable("id")), actor, body.comment()));
                })
                .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> pipeline(ServerRequest request) {
        return service.pipelineStats().flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> flush(ServerRequest request) {
        return request
                .bodyToMono(FlushRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests
                        .userId(request)
                        .flatMap(actor -> service.flush(body.conversationId(), actor)))
                .then(ServerResponse.accepted().build());
    }

    private Mono<ServerResponse> rebuild(ServerRequest request) {
        return request
                .bodyToMono(RebuildRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests
                        .userId(request)
                        .flatMap(actor -> service.rebuild(
                                body.conversationId(),
                                body.teamId(),
                                body.agentId(),
                                body.level(),
                                body.afterL1Id(),
                                actor)))
                .flatMap(result -> ServerResponse.accepted().bodyValue(result));
    }

    private Mono<ServerResponse> export(ServerRequest request) {
        var rows = service.export(
                query(request, "teamId"),
                query(request, "agentId"),
                query(request, "userId"),
                type(request),
                memoryStatus(request),
                instantParam(request, "from"),
                instantParam(request, "to"),
                AiEndpointRequests.intParam(request, "limit", 5000, 1, 10000));
        return ServerResponse.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, CSV_CONTENT_DISPOSITION)
                .body(rows, String.class);
    }

    private static long beforeId(ServerRequest request) {
        return AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
    }

    private static @Nullable Instant instantParam(ServerRequest request, String name) {
        var value = query(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw invalid(name + " 时间格式不正确。");
        }
    }

    private static int limit(ServerRequest request) {
        return AiEndpointRequests.intParam(request, "limit", 20, 1, 100);
    }

    private static String query(ServerRequest request, String name) {
        return request.queryParam(name).filter(StringUtils::hasText).orElse(null);
    }

    private static MemoryType type(ServerRequest request) {
        var value = query(request, "type");
        if (value == null) {
            return null;
        }
        try {
            return MemoryType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("type 不合法。");
        }
    }

    private static MemoryStatus memoryStatus(ServerRequest request) {
        var value = query(request, "status");
        if (value == null) {
            return null;
        }
        try {
            return MemoryStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("status 不合法。");
        }
    }

    private static MemoryRequestStatus requestStatus(ServerRequest request) {
        var value = query(request, "status");
        if (value == null) {
            return null;
        }
        try {
            return MemoryRequestStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("status 不合法。");
        }
    }

    private static MemoryRequestType requestType(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("requestType 不能为空。");
        }
        try {
            return MemoryRequestType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("requestType 不合法。");
        }
    }

    private static long numericId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid("申请标识格式不正确。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, message);
    }

    public record CorrectionRequest(String requestType, String reason, String suggestedContent) {}

    public record FlushRequest(long conversationId) {}

    public record RebuildRequest(
            @Nullable Long conversationId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String level,
            @Nullable Long afterL1Id) {}

    public record ReviewRequest(String comment) {}
}
