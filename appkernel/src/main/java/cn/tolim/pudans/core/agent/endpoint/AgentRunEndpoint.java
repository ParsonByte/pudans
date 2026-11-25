package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.AgentApproval;
import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.contract.agent.dto.AgentApprovalDecisionRequest;
import cn.tolim.pudans.contract.agent.dto.AgentRunEvent;
import cn.tolim.pudans.contract.agent.dto.AgentRunStartRequest;
import cn.tolim.pudans.core.agent.runtime.AgentRunEvents;
import cn.tolim.pudans.core.agent.runtime.AgentRuns;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
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
public class AgentRunEndpoint implements CustomEndpoint {

    private static final String TAG = "AgentRunV1alpha1";

    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private final AgentRuns runs;

    private final AgentRunEvents runEvents;

    private final ReactiveRequestIdentity identity;

    // 注册运行启动、状态、事件、取消和审批路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/agentruns/-/start", this::start, builder -> builder.operationId("startAgentRun")
                        .description("Start a conversation run with the configured Agent runtime.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AgentRunStartRequest.class))))
                        .response(responseBuilder().implementation(AgentRun.class)))
                .GET("/agentruns/{name}/status", this::status, builder -> builder.operationId("getAgentRunStatus")
                        .description("Get the durable state of a conversation run.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentRun.class)))
                .GET("/agentruns/{name}/events", this::runEvents, builder -> builder.operationId("streamAgentRunEvents")
                        .description("Stream run events with Last-Event-ID replay and keepalive pings.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .content(contentBuilder()
                                        .mediaType(MediaType.TEXT_EVENT_STREAM_VALUE)
                                        .schema(schemaBuilder().implementation(AgentRunEvent.class)))))
                .POST("/agentruns/{name}/cancel", this::cancel, builder -> builder.operationId("cancelAgentRun")
                        .description("Request cancellation of a conversation run.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentRun.class)))
                .POST("/agentruns/{name}/approvals/{approvalName}", this::decide, builder -> builder.operationId("decideAgentApproval")
                        .description("Approve or reject a pending high-risk tool request.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AgentApprovalDecisionRequest.class))))
                        .response(responseBuilder().implementation(AgentApproval.class)))
                .build();
    }

    // 启动需要已配置运行时的 Agent 运行。
    private Mono<ServerResponse> start(ServerRequest request) {
        return request.bodyToMono(AgentRunStartRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> Mono.zip(identity.resolve(request), identity.roles())
                        .flatMap(identity -> runs.start(identity.getT1(), identity.getT2(), body)))
                .flatMap(run -> ServerResponse.status(HttpStatus.CREATED).bodyValue(run));
    }

    // 返回运行状态。
    private Mono<ServerResponse> status(ServerRequest request) {
        var runId = uuid(request.pathVariable("name"), "agent-run-id-invalid");
        return identity
                .resolve(request)
                .flatMap(owner -> runs.status(owner, runId))
                .flatMap(run -> ServerResponse.ok().bodyValue(run));
    }

    // 先校验事件游标，再解析归属并重放运行事件。
    private Mono<ServerResponse> runEvents(ServerRequest request) {
        return Mono.defer(() -> {
            var runId = uuid(request.pathVariable("name"), "agent-run-id-invalid");
            var lastEventId = request.headers().firstHeader(LAST_EVENT_ID_HEADER);
            if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "agent-event-id-invalid",
                        "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
            }
            return identity
                    .resolve(request)
                    .flatMap(owner -> runs.status(owner, runId))
                    .flatMap(run -> ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .header("Cache-Control", "no-cache")
                            .header("X-Accel-Buffering", "no")
                            .body(
                                    runEvents.stream(run, lastEventId)
                                            .map(AgentRunEndpoint::serverSentEvent),
                                    ServerSentEvent.class));
        });
    }

    // 请求取消运行。
    private Mono<ServerResponse> cancel(ServerRequest request) {
        var runId = uuid(request.pathVariable("name"), "agent-run-id-invalid");
        return Mono.zip(identity.resolve(request), identity.roles())
                .flatMap(identity -> runs.cancel(identity.getT1(), identity.getT2(), runId))
                .flatMap(run -> ServerResponse.ok().bodyValue(run));
    }

    // 处理待审批的高风险工具请求。
    private Mono<ServerResponse> decide(ServerRequest request) {
        var runId = uuid(request.pathVariable("name"), "agent-run-id-invalid");
        var approvalId = uuid(request.pathVariable("approvalName"), "agent-approval-id-invalid");
        return request.bodyToMono(AgentApprovalDecisionRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> Mono.zip(identity.resolve(request), identity.roles())
                        .flatMap(identity -> runs.decide(
                                identity.getT1(), identity.getT2(), runId, approvalId, body)))
                .flatMap(approval -> ServerResponse.ok().bodyValue(approval));
    }

    private static ServerSentEvent<AgentRunEvent> serverSentEvent(AgentRunEvent event) {
        var builder = ServerSentEvent.<AgentRunEvent>builder()
                .event(event.type())
                .data(event);
        if (AiEventStreams.validId(event.eventId())) {
            builder.id(event.eventId());
        }
        return builder.build();
    }

    private static UUID uuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "资源标识不是有效 UUID。");
        }
    }
}
