package cn.tolim.pudans.core.ai.runtime.endpoint;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH;
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes.RuntimeView;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes.SnapshotView;
import cn.tolim.pudans.core.ai.runtime.SandboxProviderRegistry;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxErrors;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecResult;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFile;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiRuntimeEndpoint implements CustomEndpoint {

    private static final String TAG = "AiRuntimeV1alpha1";

    private static final int DEFAULT_LIST_LIMIT = 20;

    private static final int MAX_LIST_LIMIT = 100;

    private final AgentRuntimes agentRuntimes;

    private final AiEndpointRequests requests;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/airuntimes", this::list, builder -> builder
                        .operationId("listAiRuntimes")
                        .description("List Agent runtime instances visible to the current user.")
                        .tag(TAG)
                        .parameter(limitParameter())
                        .parameter(beforeIdParameter())
                        .response(responseBuilder().implementationArray(RuntimeView.class)))
                .GET("/airuntimes/-/providers", this::providers, builder -> builder
                        .operationId("listAiRuntimeProviders")
                        .description("List enabled sandbox providers and their capabilities.")
                        .tag(TAG)
                        .response(responseBuilder().implementationArray(SandboxProviderRegistry.ProviderInfo.class)))
                .GET("/airuntimes/{agent}", this::get, builder -> builder
                        .operationId("getAiRuntime")
                        .description("Get the runtime state of an Agent.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(RuntimeView.class)))
                .POST("/airuntimes/{agent}/start", this::start, builder -> builder
                        .operationId("startAiRuntime")
                        .description("Create the sandbox if needed and start it. Repeated calls return the current state.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(RuntimeView.class)))
                .POST("/airuntimes/{agent}/stop", this::stop, builder -> builder
                        .operationId("stopAiRuntime")
                        .description("Stop and destroy the Agent sandbox.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(RuntimeView.class)))
                .POST("/airuntimes/{agent}/restart", this::restart, builder -> builder
                        .operationId("restartAiRuntime")
                        .description("Destroy the current sandbox and create a new one.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(RuntimeView.class)))
                .DELETE("/airuntimes/{agent}", this::delete, builder -> builder
                        .operationId("deleteAiRuntime")
                        .description("Destroy the sandbox and delete the runtime record.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().description("Runtime deleted.")))
                .POST("/airuntimes/{agent}/exec", this::exec, builder -> builder
                        .operationId("execAiRuntimeCommand")
                        .description("Execute a command inside the Agent sandbox.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(ExecRequest.class))
                        .response(responseBuilder().implementation(SandboxExecResult.class)))
                .GET("/airuntimes/{agent}/files", this::readFile, builder -> builder
                        .operationId("readAiRuntimeFile")
                        .description("Read a file from the Agent sandbox. Binary content is base64 encoded.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .parameter(parameterBuilder().in(QUERY).name("path").required(true))
                        .response(responseBuilder().implementation(SandboxFile.class)))
                .POST("/airuntimes/{agent}/files", this::writeFile, builder -> builder
                        .operationId("writeAiRuntimeFile")
                        .description("Write or overwrite a file in the Agent sandbox.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(WriteFileRequest.class))
                        .response(responseBuilder().description("File written.")))
                .GET("/airuntimes/{agent}/logs", this::logs, builder -> builder
                        .operationId("streamAiRuntimeLogs")
                        .description("Stream sandbox logs as server-sent events.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .parameter(parameterBuilder().in(QUERY).name("follow").required(false))
                        .parameter(parameterBuilder().in(QUERY).name("cursor").required(false))
                        .response(responseBuilder().description("SSE event stream with log events.")))
                .GET("/airuntimes/{agent}/snapshots", this::snapshots, builder -> builder
                        .operationId("listAiRuntimeSnapshots")
                        .description("List workspace snapshots stored for the Agent sandbox.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementationArray(SnapshotView.class)))
                .POST("/airuntimes/{agent}/snapshots", this::snapshot, builder -> builder
                        .operationId("createAiRuntimeSnapshot")
                        .description("Archive the current sandbox workspace into a stored snapshot.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(SnapshotRequest.class))
                        .response(responseBuilder().implementation(SnapshotView.class)))
                .POST("/airuntimes/{agent}/snapshots/{snapshotId}/restore", this::restoreSnapshot, builder -> builder
                        .operationId("restoreAiRuntimeSnapshot")
                        .description("Restore a stored snapshot into the running sandbox workspace.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .parameter(snapshotIdParameter())
                        .response(responseBuilder().description("Snapshot restored.")))
                .DELETE("/airuntimes/{agent}/snapshots/{snapshotId}", this::deleteSnapshot, builder -> builder
                        .operationId("deleteAiRuntimeSnapshot")
                        .description("Delete a stored sandbox snapshot.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .parameter(snapshotIdParameter())
                        .response(responseBuilder().description("Snapshot deleted.")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIST_LIMIT, 1, MAX_LIST_LIMIT);
        var beforeId =
                AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 0, Long.MAX_VALUE);
        return requests
                .userId(request)
                .flatMap(userId -> ServerResponse.ok()
                        .body(agentRuntimes.list(userId, beforeId, limit), RuntimeView.class));
    }

    private Mono<ServerResponse> providers(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(userId -> ServerResponse.ok()
                        .body(agentRuntimes.providers(), SandboxProviderRegistry.ProviderInfo.class));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.get(agent, userId))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> start(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.start(agent, userId))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> stop(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.stop(agent, userId))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> restart(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.restart(agent, userId))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.delete(agent, userId))
                .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> exec(ServerRequest request) {
        var agent = agent(request);
        return request
                .bodyToMono(ExecRequest.class)
                .switchIfEmpty(Mono.error(SandboxErrors.configInvalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> agentRuntimes.exec(
                        agent, userId, body.command(), body.args(), body.workdir(), body.timeoutSeconds())))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> readFile(ServerRequest request) {
        var agent = agent(request);
        var path = request.queryParam("path").filter(StringUtils::hasText).orElse(null);
        if (path == null) {
            return Mono.error(SandboxErrors.configInvalid("查询参数 path 不能为空。"));
        }
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.readFile(agent, userId, path))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<ServerResponse> writeFile(ServerRequest request) {
        var agent = agent(request);
        return request
                .bodyToMono(WriteFileRequest.class)
                .switchIfEmpty(Mono.error(SandboxErrors.configInvalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> agentRuntimes.writeFile(
                        agent, userId, body.path(), body.encoding(), body.content())))
                .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> logs(ServerRequest request) {
        var agent = agent(request);
        var follow = Boolean.parseBoolean(
                request.queryParam("follow").orElse("false"));
        var cursor = request.queryParam("cursor").orElse(null);
        return requests.userId(request).flatMap(userId -> {
            var events = agentRuntimes
                    .logs(agent, userId, follow, cursor)
                    .map(this::logEvent);
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .header("X-Accel-Buffering", "no")
                    .body(events, String.class);
        });
    }

    private Mono<ServerResponse> snapshots(ServerRequest request) {
        var agent = agent(request);
        return requests
                .userId(request)
                .flatMapMany(userId -> agentRuntimes.snapshots(agent, userId, DEFAULT_LIST_LIMIT))
                .collectList()
                .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> snapshot(ServerRequest request) {
        var agent = agent(request);
        return request
                .bodyToMono(SnapshotRequest.class)
                .defaultIfEmpty(new SnapshotRequest(null))
                .flatMap(body -> requests.userId(request).flatMap(userId -> agentRuntimes.snapshot(
                        agent, userId, body.name(), userId)))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> restoreSnapshot(ServerRequest request) {
        var agent = agent(request);
        var snapshotId = snapshotId(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.restoreSnapshot(agent, userId, snapshotId))
                .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> deleteSnapshot(ServerRequest request) {
        var agent = agent(request);
        var snapshotId = snapshotId(request);
        return requests
                .userId(request)
                .flatMap(userId -> agentRuntimes.deleteSnapshot(agent, userId, snapshotId))
                .then(ServerResponse.noContent().build());
    }

    private static long snapshotId(ServerRequest request) {
        var value = request.pathVariable("snapshotId");
        try {
            var id = Long.parseLong(value);
            if (id <= 0) {
                throw SandboxErrors.configInvalid("snapshotId 必须为正数。");
            }
            return id;
        } catch (NumberFormatException error) {
            throw SandboxErrors.configInvalid("snapshotId 必须是整数。");
        }
    }

    private String logEvent(Object event) {
        try {
            return "event: log\ndata: " + mapper.writeValueAsString(event) + "\n\n";
        } catch (JsonProcessingException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ai-runtime-log-invalid", "日志事件无法序列化。");
        }
    }

    private static String agent(ServerRequest request) {
        var agent = request.pathVariable("agent");
        if (!StringUtils.hasText(agent)) {
            throw SandboxErrors.configInvalid("agent 不能为空。");
        }
        return agent;
    }

    private static Builder agentParameter() {
        return parameterBuilder().in(PATH).name("agent").required(true);
    }

    private static Builder limitParameter() {
        return parameterBuilder().in(QUERY).name("limit").required(false);
    }

    private static Builder beforeIdParameter() {
        return parameterBuilder().in(QUERY).name("beforeId").required(false);
    }

    private static Builder snapshotIdParameter() {
        return parameterBuilder().in(PATH).name("snapshotId").required(true);
    }

    public record ExecRequest(
            String command, @Nullable List<String> args, @Nullable String workdir, @Nullable Integer timeoutSeconds) {}

    public record WriteFileRequest(String path, @Nullable String encoding, String content) {}

    public record SnapshotRequest(@Nullable String name) {}
}