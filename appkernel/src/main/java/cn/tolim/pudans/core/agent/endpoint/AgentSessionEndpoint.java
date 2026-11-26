package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.AgentSession;
import cn.tolim.pudans.contract.agent.dto.AgentMessagePage;
import cn.tolim.pudans.contract.agent.dto.AgentSessionCreateRequest;
import cn.tolim.pudans.contract.agent.dto.AgentSessionPage;
import cn.tolim.pudans.contract.agent.dto.AgentSessionSummary;
import cn.tolim.pudans.contract.agent.dto.AgentSessionUpdateRequest;
import cn.tolim.pudans.core.agent.runtime.AgentSessions;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.UUID;
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
public class AgentSessionEndpoint implements CustomEndpoint {

    private static final String TAG = "AgentSessionV1alpha1";

    private final AgentSessions sessions;

    private final ReactiveRequestIdentity identity;

    // 注册会话创建、列表、汇总、详情、元数据、归档和消息路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/agentsessions/-/create", this::create, builder -> builder.operationId("createAgentSession")
                        .description("Create a conversation session for the current owner and workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AgentSessionCreateRequest.class))))
                        .response(responseBuilder().implementation(AgentSession.class)))
                .GET("/agentsessions/-/mine", this::mine, builder -> builder.operationId("listAgentSessions")
                        .description("List conversation sessions owned by the current user.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSessionPage.class)))
                .GET("/agentsessions/-/all", this::all, builder -> builder.operationId("listAllAgentSessions")
                        .description("List conversation sessions across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSessionPage.class)))
                .GET("/agentsessions/-/summary", this::summary, builder -> builder.operationId("summarizeAgentSessions")
                        .description("Summarize the current user's conversation sessions.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSessionSummary.class)))
                .GET("/agentsessions/-/all-summary", this::allSummary, builder -> builder
                        .operationId("summarizeAllAgentSessions")
                        .description("Summarize conversation sessions across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSessionSummary.class)))
                .GET("/agentsessions/{name}/view", this::view, builder -> builder.operationId("getAgentSession")
                        .description("Get one conversation session.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSession.class)))
                .PATCH("/agentsessions/{name}/-/metadata", this::metadata, builder -> builder.operationId("updateAgentSessionMetadata")
                        .description("Update the title, profile or default model of a conversation session.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AgentSessionUpdateRequest.class))))
                        .response(responseBuilder().implementation(AgentSession.class)))
                .POST("/agentsessions/{name}/-/archive", this::archive, builder -> builder.operationId("archiveAgentSession")
                        .description("Archive a conversation session.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentSession.class)))
                .GET("/agentsessions/{name}/messages", this::messages, builder -> builder.operationId("listAgentSessionMessages")
                        .description("List messages in reverse chronological order with a time cursor.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AgentMessagePage.class)))
                .build();
    }

    // 创建当前身份下的新会话。
    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AgentSessionCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request).flatMap(owner -> sessions.create(owner, body)))
                .flatMap(session -> ServerResponse.status(HttpStatus.CREATED).bodyValue(session));
    }

    // 返回当前身份可见的会话列表。
    private Mono<ServerResponse> mine(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> sessions.list(owner, limit(request)))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    // 返回全平台会话列表。
    private Mono<ServerResponse> all(ServerRequest request) {
        return sessions
                .listAll(limit(request))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    // 返回当前身份的会话统计。
    private Mono<ServerResponse> summary(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(sessions::summary)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 返回全平台会话统计。
    private Mono<ServerResponse> allSummary(ServerRequest request) {
        return sessions.summaryAll().flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 读取单个会话详情。
    private Mono<ServerResponse> view(ServerRequest request) {
        var sessionId = uuid(request.pathVariable("name"), "agent-session-id-invalid");
        return identity
                .resolve(request)
                .flatMap(owner -> sessions.view(owner, sessionId))
                .flatMap(session -> ServerResponse.ok().bodyValue(session));
    }

    // 更新会话展示元数据。
    private Mono<ServerResponse> metadata(ServerRequest request) {
        var sessionId = uuid(request.pathVariable("name"), "agent-session-id-invalid");
        return request.bodyToMono(AgentSessionUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity
                        .resolve(request)
                        .flatMap(owner -> sessions.updateMetadata(owner, sessionId, body)))
                .flatMap(session -> ServerResponse.ok().bodyValue(session));
    }

    // 归档会话并保留历史消息。
    private Mono<ServerResponse> archive(ServerRequest request) {
        var sessionId = uuid(request.pathVariable("name"), "agent-session-id-invalid");
        return identity
                .resolve(request)
                .flatMap(owner -> sessions.archive(owner, sessionId))
                .flatMap(session -> ServerResponse.ok().bodyValue(session));
    }

    // 分页读取会话消息。
    private Mono<ServerResponse> messages(ServerRequest request) {
        var sessionId = uuid(request.pathVariable("name"), "agent-session-id-invalid");
        var before = request.queryParam("before").orElse(null);
        return identity
                .resolve(request)
                .flatMap(owner -> sessions.messages(owner, sessionId, before, limit(request)))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private static int limit(ServerRequest request) {
        var raw = request.queryParam("limit").orElse(null);
        if (raw == null || raw.isBlank()) {
            return AgentSessions.normalizeLimit(0);
        }
        try {
            return AgentSessions.normalizeLimit(Integer.parseInt(raw));
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("查询参数 'limit' 必须是整数。");
        }
    }

    private static UUID uuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "资源标识不是有效 UUID。");
        }
    }
}
