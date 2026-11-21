package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import cn.tolim.pudans.core.ai.protocol.agentprotocol.AgentProtocolRunStore.RunRecord;
import cn.tolim.pudans.core.ai.protocol.agentprotocol.AgentProtocolRunStore.RunSnapshot;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.UserMessage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AgentProtocolEndpoint implements CustomEndpoint {

    private static final String TAG = "AgentProtocolV1alpha1";

    private static final String CODE_REQUEST_INVALID = "ai-agent-protocol-request-invalid";

    private static final String CODE_RUN_NOT_FOUND = "ai-agent-protocol-run-not-found";

    private static final String ROLE_USER = "user";

    private final ProtocolAgentRuntime runtime;

    private final AgentProtocolRunStore store;

    private final AiEndpointRequests requests;

    private final AgentProtocolEventMapper eventMapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiagentprotocol/-/runs", this::create, builder -> builder
                        .operationId("createAgentProtocolRun")
                        .description("Start an agent run; stream=true returns an SSE event stream.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(RunRequest.class))
                        .response(responseBuilder().implementation(RunView.class)))
                .GET("/aiagentprotocol/-/runs/{runId}", this::get, builder -> builder
                        .operationId("getAgentProtocolRun")
                        .description("Get the status and result of a run.")
                        .tag(TAG)
                        .parameter(runIdParameter())
                        .response(responseBuilder().implementation(RunStateView.class)))
                .POST("/aiagentprotocol/-/runs/{runId}/cancel", this::cancel, builder -> builder
                        .operationId("cancelAgentProtocolRun")
                        .description("Cancel a pending or running run.")
                        .tag(TAG)
                        .parameter(runIdParameter())
                        .response(responseBuilder().implementation(RunView.class)))
                .GET("/aiagentprotocol/-/threads/{threadId}/state", this::threadState, builder -> builder
                        .operationId("getAgentProtocolThreadState")
                        .description("Get the latest run summary for a thread.")
                        .tag(TAG)
                        .parameter(threadIdParameter())
                        .response(responseBuilder().implementation(RunStateView.class)))
                .build();
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return requests
                .userId(request)
                .zipWith(body(request))
                .flatMap(tuple -> start(tuple.getT1(), tuple.getT2()));
    }

    private Mono<ServerResponse> start(String userId, RunRequest body) {
        var runId = UUID.randomUUID().toString();
        var threadId = StringUtils.hasText(body.threadId()) ? body.threadId() : UUID.randomUUID().toString();
        var protocolRequest = new ProtocolAgentRuntime.ProtocolRunRequest(
                userId,
                body.agentId(),
                threadId,
                null,
                inputMessage(body.input()),
                Boolean.TRUE.equals(body.stream()),
                Map.of());
        return runtime.start(protocolRequest).flatMap(run -> store
                .create(userId, runId, threadId, body.agentId(), run)
                .flatMap(record -> {
                    if (Boolean.TRUE.equals(body.stream())) {
                        record.markRunning();
                        return ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                                .header("X-Accel-Buffering", "no")
                                .body(stream(record), String.class);
                    }
                    drive(record);
                    return ServerResponse.ok().bodyValue(RunView.from(record.snapshot()));
                }));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        var runId = request.pathVariable("runId");
        return requests
                .userId(request)
                .flatMap(userId -> store
                        .find(userId, runId)
                        .switchIfEmpty(Mono.error(notFound()))
                        .flatMap(snapshot -> ServerResponse.ok().bodyValue(RunStateView.from(snapshot))));
    }

    private Mono<ServerResponse> cancel(ServerRequest request) {
        var runId = request.pathVariable("runId");
        return requests
                .userId(request)
                .flatMap(userId -> store
                        .cancel(userId, runId)
                        .switchIfEmpty(Mono.error(notFound()))
                        .flatMap(snapshot -> ServerResponse.ok().bodyValue(RunView.from(snapshot))));
    }

    private Mono<ServerResponse> threadState(ServerRequest request) {
        var threadId = request.pathVariable("threadId");
        return requests
                .userId(request)
                .flatMap(userId -> store
                        .latestByThread(userId, threadId)
                        .switchIfEmpty(Mono.error(notFound()))
                        .flatMap(snapshot -> ServerResponse.ok().bodyValue(RunStateView.from(snapshot))));
    }

    private void drive(RunRecord record) {
        record.markRunning();
        record
                .run()
                .result()
                .subscribe(
                        result -> store.complete(record.runId(), result.content()).subscribe(),
                        error -> store.fail(record.runId(), errorMessage(error)).subscribe());
    }

    private Flux<String> stream(RunRecord record) {
        return Flux.create(
                sink -> {
                    sink.next(eventMapper.metadata(
                            record.runId(), record.threadId(), record.agentId(), record.snapshot().status()));
                    record.run().onEvent(event -> {
                        var frame = eventMapper.map(event);
                        if (frame != null) {
                            sink.next(frame);
                        }
                    });
                    var executionRef =
                            new AtomicReference<Disposable>();
                    sink.onCancel(() -> {
                        record.cancel();
                        var disposable = executionRef.get();
                        if (disposable != null) {
                            disposable.dispose();
                        }
                    });
                    // 结果流只订阅一次，结束事件由同一个订阅驱动。
                    executionRef.set(record
                            .run()
                            .result()
                            .subscribe(
                                    result -> {
                                        store.complete(record.runId(), result.content()).subscribe();
                                        sink.next(eventMapper.end(
                                                record.runId(),
                                                record.threadId(),
                                                record.snapshot().status(),
                                                result.content()));
                                        sink.complete();
                                    },
                                    error -> {
                                        store.fail(record.runId(), errorMessage(error)).subscribe();
                                        sink.next(eventMapper.error(
                                                record.runId(),
                                                record.threadId(),
                                                record.snapshot().status(),
                                                errorMessage(error)));
                                        sink.complete();
                                    }));
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    private static Mono<RunRequest> body(ServerRequest request) {
        return request
                .bodyToMono(RunRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .onErrorMap(DecodingException.class, error -> invalid("请求体格式不正确。"))
                .onErrorMap(ServerWebInputException.class, error -> invalid("请求体格式不正确。"));
    }

    private static UserMessage inputMessage(@Nullable Object input) {
        if (input == null) {
            throw invalid("input 不能为空。");
        }
        if (input instanceof String text) {
            return userMessage(text);
        }
        if (input instanceof Map<?, ?> object && object.get("messages") instanceof List<?> messages) {
            return messages(messages);
        }
        throw invalid("input 必须是字符串或包含 messages 的对象。");
    }

    private static UserMessage messages(List<?> messages) {
        if (messages.isEmpty()) {
            throw invalid("messages 必须是非空数组。");
        }
        UserMessage input = null;
        for (var item : messages) {
            if (!(item instanceof Map<?, ?> message)) {
                throw invalid("messages 中的消息必须是对象。");
            }
            if (!ROLE_USER.equals(message.get("role"))) {
                continue;
            }
            if (!(message.get("content") instanceof String content)) {
                throw invalid("user 消息的 content 必须是字符串。");
            }
            input = userMessage(content);
        }
        if (input == null) {
            throw invalid("messages 中缺少 user 消息。");
        }
        return input;
    }

    private static UserMessage userMessage(String text) {
        if (!StringUtils.hasText(text)) {
            throw invalid("输入内容不能为空。");
        }
        return UserMessage.from(text);
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_REQUEST_INVALID, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_RUN_NOT_FOUND, "运行或线程不存在。");
    }

    private static Builder runIdParameter() {
        return parameterBuilder().in(PATH).name("runId").required(true);
    }

    private static Builder threadIdParameter() {
        return parameterBuilder().in(PATH).name("threadId").required(true);
    }

    public record RunRequest(
            @JsonProperty("agent_id") @Nullable String agentId,
            @JsonProperty("thread_id") @Nullable String threadId,
            @Nullable Object input,
            @Nullable Boolean stream) {}

    public record RunView(
            @JsonProperty("run_id") String runId,
            @JsonProperty("thread_id") String threadId,
            String status) {

        static RunView from(RunSnapshot snapshot) {
            return new RunView(snapshot.runId(), snapshot.threadId(), snapshot.status().code());
        }
    }

    public record RunStateView(
            @JsonProperty("run_id") String runId,
            @JsonProperty("thread_id") String threadId,
            @JsonProperty("agent_id") @Nullable String agentId,
            String status,
            @Nullable String content,
            @Nullable String error) {

        static RunStateView from(RunSnapshot snapshot) {
            return new RunStateView(
                    snapshot.runId(),
                    snapshot.threadId(),
                    snapshot.agentId(),
                    snapshot.status().code(),
                    snapshot.content(),
                    snapshot.error());
        }
    }
}