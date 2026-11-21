package cn.tolim.pudans.core.ai.protocol.a2a;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class A2aEndpoint implements CustomEndpoint {

    private static final String TAG = "AiA2aV1alpha1";

    private static final String AGENT_CARD_SUFFIX = "/.well-known/agent.json";

    private static final String CARD_DESCRIPTION = "Pudans A2A agent endpoint.";

    private static final String CARD_VERSION = "1.0.0";

    private static final String CARD_MODE_TEXT = "text";

    private static final String METHOD_MESSAGE_SEND = "message/send";

    private static final String METHOD_MESSAGE_STREAM = "message/stream";

    private static final String METHOD_TASKS_GET = "tasks/get";

    private static final String METHOD_TASKS_CANCEL = "tasks/cancel";

    private static final String CODE_ENCODE_FAILED = "ai-a2a-encode-failed";

    private final A2aRequestHandler handler;

    private final AiEndpointRequests requests;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aia2a/.well-known/agent.json", this::agentCard, builder -> builder
                        .operationId("getA2aAgentCard")
                        .description("A2A agent card discovery document.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(QUERY).name("agent").required(false))
                        .response(responseBuilder().implementation(A2aAgentCard.class)))
                .POST("/aia2a", this::rpc, builder -> builder
                        .operationId("handleA2aJsonRpc")
                        .description("A2A JSON-RPC 2.0 endpoint for messaging and task operations.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true))
                        .response(responseBuilder()))
                .build();
    }

    private Mono<ServerResponse> agentCard(ServerRequest request) {
        var agentName = request.queryParam("agent")
                .filter(StringUtils::hasText)
                .orElse(Teams.DEFAULT_AGENT);
        return requests
                .userId(request)
                .map(userId -> card(agentName, request))
                .flatMap(card -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(card));
    }

    private Mono<ServerResponse> rpc(ServerRequest request) {
        return request.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {
            A2aJsonRpcRequest rpc;
            try {
                rpc = A2aJsonRpcRequest.parse(body, mapper);
            } catch (A2aRpcException error) {
                return respond(A2aJsonRpcResponse.error(error.id(), error.code(), error.getMessage()));
            }
            return requests.userId(request).flatMap(userId -> {
                try {
                    return dispatch(rpc, userId);
                } catch (A2aRpcException error) {
                    return respond(A2aJsonRpcResponse.error(rpc.id(), error.code(), error.getMessage()));
                }
            });
        });
    }

    private Mono<ServerResponse> dispatch(A2aJsonRpcRequest rpc, String userId) {
        return switch (rpc.method()) {
            case METHOD_MESSAGE_SEND -> send(rpc, userId);
            case METHOD_MESSAGE_STREAM -> stream(rpc, userId);
            case METHOD_TASKS_GET -> taskResponse(rpc, handler.task(userId, rpc.params()));
            case METHOD_TASKS_CANCEL -> taskResponse(rpc, handler.cancel(userId, rpc.params()));
            default -> respond(A2aJsonRpcResponse.error(
                    rpc.id(), A2aRpcException.METHOD_NOT_FOUND, "未知方法：" + rpc.method()));
        };
    }

    private Mono<ServerResponse> taskResponse(A2aJsonRpcRequest rpc, Mono<A2aTask> task) {
        return task.map(result -> A2aJsonRpcResponse.result(rpc.id(), result))
                .onErrorResume(A2aRpcException.class, error -> Mono.just(
                        A2aJsonRpcResponse.error(rpc.id(), error.code(), error.getMessage())))
                .onErrorResume(error -> Mono.just(A2aJsonRpcResponse.error(
                        rpc.id(), A2aRpcException.INTERNAL_ERROR, message(error))))
                .flatMap(this::respond);
    }

    private Mono<ServerResponse> send(A2aJsonRpcRequest rpc, String userId) {
        var params = A2aSendParams.parse(rpc.params());
        return handler
                .send(userId, params)
                .map(task -> A2aJsonRpcResponse.result(rpc.id(), task))
                .onErrorResume(error -> Mono.just(A2aJsonRpcResponse.error(
                        rpc.id(), A2aRpcException.INTERNAL_ERROR, message(error))))
                .flatMap(this::respond);
    }

    private Mono<ServerResponse> stream(A2aJsonRpcRequest rpc, String userId) {
        var params = A2aSendParams.parse(rpc.params());
        var frames = handler
                .stream(userId, params)
                .map(event -> frame(rpc.id(), event))
                .onErrorResume(error -> Flux.just(
                        frame(rpc.id(), A2aRpcException.INTERNAL_ERROR, message(error))));
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(frames, new ParameterizedTypeReference<ServerSentEvent<String>>() {});
    }

    private Mono<ServerResponse> respond(A2aJsonRpcResponse response) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
    }

    private ServerSentEvent<String> frame(Object id, Object result) {
        return ServerSentEvent.builder(json(A2aJsonRpcResponse.result(id, result))).build();
    }

    private ServerSentEvent<String> frame(Object id, int code, String message) {
        return ServerSentEvent.builder(json(A2aJsonRpcResponse.error(id, code, message))).build();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, CODE_ENCODE_FAILED, "A2A 响应无法序列化。", error);
        }
    }

    private static A2aAgentCard card(String agentName, ServerRequest request) {
        return new A2aAgentCard(
                agentName,
                CARD_DESCRIPTION,
                endpointUrl(request),
                CARD_VERSION,
                new A2aAgentCard.Capabilities(true, false),
                List.of(CARD_MODE_TEXT),
                List.of(CARD_MODE_TEXT),
                List.of());
    }

    private static String endpointUrl(ServerRequest request) {
        var uri = request.uri();
        var path = uri.getPath();
        var endpointPath = path.endsWith(AGENT_CARD_SUFFIX)
                ? path.substring(0, path.length() - AGENT_CARD_SUFFIX.length())
                : path;
        return UriComponentsBuilder.fromUri(uri)
                .replacePath(endpointPath)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    private static String message(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? message : "A2A 运行失败。";
    }
}