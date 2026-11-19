package cn.tolim.pudans.core.ai.chat.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.chat.AiChatCommand;
import cn.tolim.pudans.core.ai.chat.AiChatCompletionRequest;
import cn.tolim.pudans.core.ai.chat.AiChatCompletionResult;
import cn.tolim.pudans.core.ai.chat.AiChatService;
import cn.tolim.pudans.core.ai.chat.AiChatStreamEvent;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChatEndpoint implements CustomEndpoint {

    private static final String TAG = "ChatV1alpha1";

    private static final String CODE_BODY_INVALID = "ai-chat-request-invalid";

    private final AiChatService chatService;

    private final AiEndpointRequests requests;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/chats/-/completions", this::complete, builder -> builder
                        .operationId("createChatCompletion")
                        .description("Run a non-streaming chat completion.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(AiChatCompletionRequest.class))
                        .response(responseBuilder().implementation(AiChatCompletionResult.class)))
                .POST("/chats/-/completions/stream", this::stream, builder -> builder
                        .operationId("createChatCompletionStream")
                        .description("Run a streaming chat completion over SSE.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(AiChatCompletionRequest.class))
                        .response(responseBuilder()))
                .build();
    }

    private Mono<ServerResponse> complete(ServerRequest request) {
        return command(request).flatMap(chatService::complete)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> stream(ServerRequest request) {
        return command(request).flatMap(command -> {
            var body = chatService.stream(command).map(this::encode);
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Accel-Buffering", "no")
                    .body(body, String.class);
        });
    }

    private Mono<AiChatCommand> command(ServerRequest request) {
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .zipWith(body(request))
                .flatMap(tuple -> roles().map(roleNames -> new AiChatCommand(
                        tuple.getT1(), roleNames, null, tuple.getT2())));
    }

    private static Mono<AiChatCompletionRequest> body(ServerRequest request) {
        return request.bodyToMono(AiChatCompletionRequest.class)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体不能为空。")))
                .onErrorMap(
                        org.springframework.core.codec.DecodingException.class,
                        error -> new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体格式不正确。"));
    }

    private static Mono<Set<String>> roles() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()))
                .defaultIfEmpty(Set.of());
    }

    private String encode(AiChatStreamEvent event) {
        return switch (event) {
            case AiChatStreamEvent.Meta meta -> event("meta", metaJson(meta));
            case AiChatStreamEvent.Delta delta -> event("delta", deltaJson(delta));
            case AiChatStreamEvent.CitationEvent citation -> event("citation", citationJson(citation));
            case AiChatStreamEvent.Usage usage -> event("usage", usageJson(usage));
            case AiChatStreamEvent.Done done -> event("done", doneJson(done));
            case AiChatStreamEvent.Error error -> event("error", errorJson(error));
            case AiChatStreamEvent.Ping ignored -> "event: ping\ndata: {}\n\n";
        };
    }

    private ObjectNode metaJson(AiChatStreamEvent.Meta event) {
        var meta = event.meta();
        var node = mapper.createObjectNode();
        put(node, "requestId", meta.requestId());
        node.put("conversationId", meta.conversationId());
        put(node, "mode", meta.mode());
        put(node, "routeGroup", meta.routeGroup());
        put(node, "provider", meta.provider());
        put(node, "account", meta.account());
        put(node, "model", meta.model());
        put(node, "tier", meta.tier());
        put(node, "teamId", meta.teamId());
        put(node, "agentId", meta.agentId());
        put(node, "sessionKey", meta.sessionKey());
        return node;
    }

    private ObjectNode deltaJson(AiChatStreamEvent.Delta event) {
        var node = mapper.createObjectNode();
        put(node, "content", event.content());
        put(node, "reasoning", event.reasoning());
        return node;
    }

    private ObjectNode citationJson(AiChatStreamEvent.CitationEvent event) {
        var citation = event.citation();
        var node = mapper.createObjectNode();
        node.put("index", citation.index());
        node.put("docId", citation.docId());
        put(node, "fileName", citation.fileName());
        if (citation.pageNo() != null) {
            node.put("pageNo", citation.pageNo());
        }
        put(node, "headingPath", citation.headingPath());
        node.put("score", citation.score());
        put(node, "snippet", citation.snippet());
        return node;
    }

    private ObjectNode usageJson(AiChatStreamEvent.Usage event) {
        var node = mapper.createObjectNode();
        var usage = event.usage();
        if (usage != null) {
            if (usage.inputTokens() != null) {
                node.put("inputTokens", usage.inputTokens());
            }
            if (usage.outputTokens() != null) {
                node.put("outputTokens", usage.outputTokens());
            }
            if (usage.cacheReadTokens() != null) {
                node.put("cacheReadTokens", usage.cacheReadTokens());
            }
            if (usage.cacheWriteTokens() != null) {
                node.put("cacheWriteTokens", usage.cacheWriteTokens());
            }
        }
        if (event.points() != null) {
            node.put("points", event.points());
        }
        put(node, "billingSource", event.billingSource());
        return node;
    }

    private ObjectNode doneJson(AiChatStreamEvent.Done event) {
        var node = mapper.createObjectNode();
        put(node, "messageId", event.messageId());
        put(node, "finishReason", event.finishReason());
        node.put("partial", event.partial());
        return node;
    }

    private ObjectNode errorJson(AiChatStreamEvent.Error event) {
        var node = mapper.createObjectNode();
        put(node, "code", event.code());
        put(node, "message", event.message());
        node.put("retryable", event.retryable());
        node.put("partial", event.partial());
        return node;
    }

    private String event(String name, ObjectNode data) {
        try {
            return "event: " + name + "\ndata: " + mapper.writeValueAsString(data) + "\n\n";
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("无法序列化聊天事件。", error);
        }
    }

    private static void put(ObjectNode node, String name, String value) {
        if (value != null) {
            node.put(name, value);
        }
    }
}
