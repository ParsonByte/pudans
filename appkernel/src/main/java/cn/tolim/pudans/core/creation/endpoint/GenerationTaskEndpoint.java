package cn.tolim.pudans.core.creation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.creation.dto.GenerationTaskEvent;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskPage;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskResultView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStatusView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.core.creation.task.GenerationTaskAudit;
import cn.tolim.pudans.core.creation.task.GenerationTaskEvents;
import cn.tolim.pudans.core.creation.task.GenerationTasks;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GenerationTaskEndpoint implements CustomEndpoint {

    private static final String TAG = "GenerationTaskV1alpha1";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private static final int DEFAULT_LIMIT = 20;

    private final GenerationTasks tasks;

    private final GenerationTaskEvents events;

    private final ReactiveRequestIdentity identity;

    private final GenerationTaskAudit audits;

    // 注册任务启动、列表、状态、事件、结果、取消和重试路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/generationtasks/-/start", this::start, builder -> builder
                        .operationId("startGenerationTask")
                        .description("Validate and enqueue a media generation task for the current identity.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(GenerationTaskStartRequest.class))))
                        .response(responseBuilder().implementation(GenerationTaskSummary.class)))
                .GET("/generationtasks/-/mine", this::mine, builder -> builder
                        .operationId("listGenerationTasks")
                        .description("List generation tasks owned by the current user and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(GenerationTaskPage.class)))
                .GET("/generationtasks/{name}/status", this::status, builder -> builder
                        .operationId("getGenerationTaskStatus")
                        .description("Get a generation task and its attempt history.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(GenerationTaskStatusView.class)))
                .GET("/generationtasks/{name}/events", this::taskEvents, builder -> builder
                        .operationId("streamGenerationTaskEvents")
                        .description("Stream task events with Last-Event-ID replay and keepalive pings.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .content(contentBuilder().mediaType(MediaType.TEXT_EVENT_STREAM_VALUE)
                                        .schema(schemaBuilder().implementation(GenerationTaskEvent.class)))))
                .GET("/generationtasks/{name}/result", this::result, builder -> builder
                        .operationId("getGenerationTaskResult")
                        .description("Get archived result files and logical asset registration state.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(GenerationTaskResultView.class)))
                .POST("/generationtasks/{name}/cancel", this::cancel, builder -> builder
                        .operationId("cancelGenerationTask")
                        .description("Cancel a queued or provider-running generation task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(GenerationTaskSummary.class)))
                .POST("/generationtasks/{name}/-/retry", this::retry, builder -> builder
                        .operationId("retryGenerationTask")
                        .description("Safely retry a failed generation task after provider reconciliation.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(GenerationTaskSummary.class)))
                .build();
    }

    private Mono<ServerResponse> start(ServerRequest request) {
        return request
                .bodyToMono(GenerationTaskStartRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> tasks.start(
                        owner,
                        body,
                        request.headers().firstHeader(IDEMPOTENCY_HEADER))))
                .flatMap(result -> {
                    var extra = new LinkedHashMap<String, Object>();
                    extra.put("replayed", !result.created());
                    return audits
                            .record(
                                    request.exchange(),
                                    "START_GENERATION_TASK",
                                    result.task(),
                                    true,
                                    Map.copyOf(extra))
                            .thenReturn(result);
                })
                .flatMap(result -> ServerResponse.status(
                                result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .bodyValue(result.task()));
    }

    private Mono<ServerResponse> mine(ServerRequest request) {
        return Mono.defer(() -> {
            var before = before(request);
            var limit = limit(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.mine(owner, before, limit))
                    .flatMap(page -> ServerResponse.ok().bodyValue(page));
        });
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return Mono.defer(() -> {
            var taskId = uuidPath(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.status(owner, taskId))
                    .flatMap(view -> ServerResponse.ok().bodyValue(view));
        });
    }

    private Mono<ServerResponse> taskEvents(ServerRequest request) {
        return Mono.defer(() -> {
            var taskId = uuidPath(request);
            var lastEventId = request.headers().firstHeader(LAST_EVENT_ID_HEADER);
            if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "generation-event-id-invalid",
                        "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
            }
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.requireOwned(owner, taskId))
                    .flatMap(task -> ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .header("Cache-Control", "no-cache")
                            .header("X-Accel-Buffering", "no")
                            .body(
                                    events.stream(task, lastEventId)
                                            .map(event -> serverSentEvent(event)),
                                    ServerSentEvent.class));
        });
    }

    private Mono<ServerResponse> result(ServerRequest request) {
        return Mono.defer(() -> {
            var taskId = uuidPath(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.result(owner, taskId))
                    .flatMap(view -> ServerResponse.ok().bodyValue(view));
        });
    }

    private Mono<ServerResponse> cancel(ServerRequest request) {
        return Mono.defer(() -> {
            var taskId = uuidPath(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.cancel(owner, taskId))
                    .flatMap(task -> audits
                            .record(
                                    request.exchange(),
                                    "CANCEL_GENERATION_TASK",
                                    task,
                                    true,
                                    Map.of())
                            .thenReturn(task))
                    .flatMap(task -> ServerResponse.ok().bodyValue(task));
        });
    }

    private Mono<ServerResponse> retry(ServerRequest request) {
        return Mono.defer(() -> {
            var taskId = uuidPath(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> tasks.retry(owner, taskId))
                    .flatMap(task -> audits
                            .record(
                                    request.exchange(),
                                    "RETRY_GENERATION_TASK",
                                    task,
                                    true,
                                    Map.of("providerRequestSent", task.providerRequestSent()))
                            .thenReturn(task))
                    .flatMap(task -> ServerResponse.ok().bodyValue(task));
        });
    }

    private static ServerSentEvent<GenerationTaskEvent> serverSentEvent(
            GenerationTaskEvent event) {
        var builder = ServerSentEvent.<GenerationTaskEvent>builder()
                .event(event.type())
                .data(event);
        if (AiEventStreams.validId(event.eventId())) {
            builder.id(event.eventId());
        }
        return builder.build();
    }

    private static UUID uuidPath(ServerRequest request) {
        try {
            return UUID.fromString(request.pathVariable("name"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "generation-task-not-found",
                    "生成任务不存在。");
        }
    }

    private static Instant before(ServerRequest request) {
        var raw = request.queryParam("before").orElse(null);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-cursor-invalid",
                    "分页游标不是有效时间。");
        }
    }

    private static int limit(ServerRequest request) {
        var raw = request.queryParam("limit").orElse(null);
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_LIMIT;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-limit-invalid",
                    "分页数量不是有效整数。");
        }
    }
}
