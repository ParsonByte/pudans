package cn.tolim.pudans.core.ai.conversation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.session.GatewaySessions;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.MessageMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
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
public class AiConversationEndpoint implements CustomEndpoint {

    private static final String TAG = "ConversationV1alpha1";

    private static final String CODE_CONVERSATION_NOT_FOUND = "ai-conversation-not-found";

    private static final String CODE_ARTIFACT_INVALID = "ai-conversation-artifact-invalid";

    private static final Set<String> ARTIFACT_KINDS = Set.of("IMAGE", "VIDEO", "DOCUMENT", "AUDIO");

    private static final String AGENT_SOURCE_CHAT = "pudans-chat";

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private static final int MAX_MESSAGE_LIMIT = 200;

    private static final long MAX_CURSOR = Long.MAX_VALUE;

    private static final CreateConversationRequest EMPTY_REQUEST =
            new CreateConversationRequest(null, null, null, null);

    private final Conversations conversationService;

    private final Teams teams;

    private final AiSettings aiSettings;

    private final AiEndpointRequests requests;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/conversations", this::list, builder -> builder
                        .operationId("listAiConversations")
                        .description("List the current user's conversations, newest first.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false)
                                .description("Page size (1-100, default 20)."))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false)
                                .description("Return conversations whose id is lower than this cursor."))
                        .response(responseBuilder().implementationArray(ConversationView.class)))
                .POST("/conversations", this::create, builder -> builder
                        .operationId("createAiConversation")
                        .description("Create a conversation for the current user using the resolved agent.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(false)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(CreateConversationRequest.class))))
                        .response(responseBuilder().implementation(ConversationView.class)))
                .GET("/conversations/{name}", this::get, builder -> builder
                        .operationId("getAiConversation")
                        .description("Get a conversation owned by the current user.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(ConversationView.class)))
                .DELETE("/conversations/{name}", this::delete, builder -> builder
                        .operationId("deleteAiConversation")
                        .description("Soft delete a conversation owned by the current user.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().responseCode("204").description("Conversation deleted.")))
                .GET("/conversations/{name}/messages", this::messages, builder -> builder
                        .operationId("listAiConversationMessages")
                        .description("List messages of a conversation owned by the current user.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("afterId").required(false)
                                .description("Return messages whose id is greater than this cursor."))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false)
                                .description("Page size (1-200, default 50)."))
                        .response(responseBuilder().implementationArray(MessageView.class)))
                .POST("/conversations/{name}/branch", this::branch, builder -> builder
                        .operationId("branchAiConversation")
                        .description("Copy messages before the given message into a new conversation.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .requestBody(requestBodyBuilder().required(true).implementation(BranchRequest.class))
                        .response(responseBuilder().implementation(ConversationView.class)))
                .POST("/conversations/{name}/artifacts", this::artifact, builder -> builder
                        .operationId("appendAiConversationArtifact")
                        .description("Append a generated artifact as a user prompt and assistant message.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .requestBody(requestBodyBuilder().required(true).implementation(ArtifactRequest.class))
                        .response(responseBuilder().implementation(MessageView.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", MAX_CURSOR, 1, MAX_CURSOR);
        return requests.userId(request)
                .flatMap(userId -> conversationService
                        .list(userId, beforeId, limit)
                        .map(ConversationView::from)
                        .collectList())
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return requests.userId(request)
                .zipWith(request.bodyToMono(CreateConversationRequest.class).defaultIfEmpty(EMPTY_REQUEST))
                .flatMap(tuple -> createConversation(tuple.getT1(), tuple.getT2()))
                .flatMap(conversation -> ServerResponse.ok().bodyValue(ConversationView.from(conversation)));
    }

    private Mono<Conversation> createConversation(String userId, CreateConversationRequest body) {
        return resolveAgentName(body.agentName())
                .flatMap(agentName -> teams.requireAgent(agentName, userId))
                .flatMap(agent -> conversationService.createOrGet(newConversation(userId, body, agent)));
    }

    private static NewConversation newConversation(String userId, CreateConversationRequest body, AiAgent agent) {
        return new NewConversation(
                GatewaySessions.DEFAULT_SPACE,
                userId,
                agent.getSpec().getTeam(),
                agent.getMetadata().getName(),
                AGENT_SOURCE_CHAT,
                UUID.randomUUID().toString(),
                null,
                body.title(),
                body.modeCode(),
                body.routeGroup());
    }

    private Mono<String> resolveAgentName(@Nullable String agentName) {
        if (StringUtils.hasText(agentName)) {
            return Mono.just(agentName);
        }
        return aiSettings
                .defaults()
                .map(defaults -> StringUtils.hasText(defaults.getAgent())
                        ? defaults.getAgent()
                        : Teams.DEFAULT_AGENT);
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        var conversationId = conversationId(request);
        return requests.userId(request)
                .flatMap(userId -> conversationService.requireOwned(conversationId, userId))
                .flatMap(conversation -> ServerResponse.ok().bodyValue(ConversationView.from(conversation)));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        var conversationId = conversationId(request);
        return requests.userId(request)
                .flatMap(userId -> conversationService.requireOwned(conversationId, userId))
                .flatMap(conversationService::delete)
                .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> messages(ServerRequest request) {
        var conversationId = conversationId(request);
        var afterId = AiEndpointRequests.longParam(request, "afterId", 0L, 0L, MAX_CURSOR);
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_MESSAGE_LIMIT, 1, MAX_MESSAGE_LIMIT);
        return requests.userId(request)
                .flatMap(userId -> conversationService.requireOwned(conversationId, userId))
                .flatMap(conversation -> conversationService
                        .history(conversation, afterId, limit)
                        .collectList()
                        .flatMap(messages -> conversationService
                                .metadata(conversation)
                                .collectMap(MessageMetadata::messageId, MessageMetadata::metadata)
                                .map(metadata -> messages.stream()
                                        .map(message -> MessageView.from(message, metadata.get(message.id())))
                                        .toList())))
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    private Mono<ServerResponse> branch(ServerRequest request) {
        var conversationId = conversationId(request);
        return requests
                .userId(request)
                .zipWith(request
                        .bodyToMono(BranchRequest.class)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_ARTIFACT_INVALID, "请求体不能为空。"))))
                .flatMap(tuple -> conversationService
                        .requireOwned(conversationId, tuple.getT1())
                        .flatMap(conversation -> conversationService.branch(
                                conversation, tuple.getT2().messageId())))
                .flatMap(conversation -> ServerResponse.ok().bodyValue(ConversationView.from(conversation)));
    }

    private Mono<ServerResponse> artifact(ServerRequest request) {
        var conversationId = conversationId(request);
        return requests
                .userId(request)
                .zipWith(request.bodyToMono(ArtifactRequest.class).flatMap(AiConversationEndpoint::validArtifact))
                .flatMap(tuple -> conversationService
                        .requireOwned(conversationId, tuple.getT1())
                        .flatMap(conversation -> {
                            var metadata = artifactMetadata(mapper, tuple.getT2());
                            return conversationService
                                    .appendArtifact(conversation, tuple.getT2().prompt(), metadata)
                                    .map(message -> MessageView.from(message, metadata));
                        }))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private static Mono<ArtifactRequest> validArtifact(ArtifactRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_ARTIFACT_INVALID, "请求体不能为空。"));
        }
        if (!StringUtils.hasText(request.prompt())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_ARTIFACT_INVALID, "生成提示不能为空。"));
        }
        if (!StringUtils.hasText(request.kind())
                || !ARTIFACT_KINDS.contains(request.kind().toUpperCase())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_ARTIFACT_INVALID, "产物类型不受支持。"));
        }
        return Mono.just(request);
    }

    private static JsonNode artifactMetadata(ObjectMapper mapper, ArtifactRequest request) {
        var root = mapper.createObjectNode();
        var artifact = root.putObject("artifact");
        artifact.put("kind", request.kind().toUpperCase());
        if (StringUtils.hasText(request.requestId())) {
            artifact.put("requestId", request.requestId());
        }
        if (StringUtils.hasText(request.fileId())) {
            artifact.put("fileId", request.fileId());
        }
        if (StringUtils.hasText(request.url())) {
            artifact.put("url", request.url());
        }
        if (StringUtils.hasText(request.name())) {
            artifact.put("name", request.name());
        }
        if (StringUtils.hasText(request.mimeType())) {
            artifact.put("mimeType", request.mimeType());
        }
        if (request.size() != null) {
            artifact.put("size", request.size());
        }
        return root;
    }

    private static long conversationId(ServerRequest request) {
        long parsed;
        try {
            parsed = Long.parseLong(request.pathVariable("name"));
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, CODE_CONVERSATION_NOT_FOUND, "会话不存在。", error);
        }
        if (parsed <= 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, CODE_CONVERSATION_NOT_FOUND, "会话不存在。");
        }
        return parsed;
    }

    public record CreateConversationRequest(
            @Nullable String agentName,
            @Nullable String title,
            @Nullable String modeCode,
            @Nullable String routeGroup) {
    }

    public record BranchRequest(long messageId) {}

    public record ArtifactRequest(
            @Nullable String kind,
            @Nullable String prompt,
            @Nullable String requestId,
            @Nullable String fileId,
            @Nullable String url,
            @Nullable String name,
            @Nullable String mimeType,
            @Nullable Long size) {}

    public record ConversationView(
            long id,
            String userId,
            String teamId,
            String agentId,
            String agentSource,
            String sessionKey,
            @Nullable String title,
            @Nullable String modeCode,
            @Nullable String routeGroup,
            String status,
            int pendingCount,
            Instant lastActivityAt,
            Instant createdAt,
            Instant updatedAt) {

        static ConversationView from(Conversation conversation) {
            return new ConversationView(
                    conversation.id(),
                    conversation.userId(),
                    conversation.teamId(),
                    conversation.agentId(),
                    conversation.agentSource(),
                    conversation.sessionKey(),
                    conversation.title(),
                    conversation.modeCode(),
                    conversation.routeGroup(),
                    conversation.status(),
                    conversation.pendingCount(),
                    conversation.lastActivityAt(),
                    conversation.createdAt(),
                    conversation.updatedAt());
        }
    }

    public record MessageView(
            long id,
            long conversationId,
            int turnIndex,
            int messageIndex,
            String role,
            @Nullable String content,
            String contentType,
            @Nullable Integer tokenCount,
            @Nullable String modelCode,
            @Nullable JsonNode metadata,
            Instant createdAt) {

        static MessageView from(Message message, @Nullable JsonNode metadata) {
            return new MessageView(
                    message.id(),
                    message.conversationId(),
                    message.turnIndex(),
                    message.messageIndex(),
                    message.role(),
                    message.content(),
                    message.contentType(),
                    message.tokenCount(),
                    message.modelCode(),
                    metadata,
                    message.createdAt());
        }
    }
}
