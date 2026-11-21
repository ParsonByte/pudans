package cn.tolim.pudans.core.ai.chat.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.core.ai.chat.AiChatCommand;
import cn.tolim.pudans.core.ai.chat.AiChatCompletionRequest;
import cn.tolim.pudans.core.ai.chat.AiChatCompletionResult;
import cn.tolim.pudans.core.ai.chat.ChatCompletions;
import cn.tolim.pudans.core.ai.chat.AiChatStreamEvent;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChatEndpoint implements CustomEndpoint {

    private static final String TAG = "ChatV1alpha1";

    private static final String CODE_BODY_INVALID = "ai-chat-request-invalid";

    private static final String CODE_ATTACHMENT_INVALID = "ai-chat-attachment-invalid";

    private static final int MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;

    private static final Set<String> UNSAFE_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/javascript",
            "application/javascript");

    private static final String ATTACHMENT_MODULE = "ai-chat";

    private final ChatCompletions chatCompletions;

    private final AiEndpointRequests requests;

    private final StorageTransfer storageTransfer;

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
                .POST("/chats/-/tool-confirmations", this::confirmTool, builder -> builder
                        .operationId("confirmChatTool")
                        .description("Approve or deny a pending tool call.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ToolConfirmation.class))
                        .response(responseBuilder()))
                .POST("/chats/-/tool-external-results", this::submitExternalResult, builder -> builder
                        .operationId("submitChatToolExternalResult")
                        .description("Submit the result of an externally executed tool call.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true)
                                .implementation(ToolExternalResultRequest.class))
                        .response(responseBuilder()))
                .POST("/chats/-/runs/{runId}/interrupt", this::interruptRun, builder -> builder
                        .operationId("interruptChatRun")
                        .description("Interrupt an active chat agent run.")
                        .tag(TAG)
                        .response(responseBuilder()))
                .POST("/chats/-/attachments", this::uploadAttachment, builder -> builder
                        .operationId("uploadChatAttachment")
                        .description("Upload a multimodal attachment for a chat message.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AttachmentView.class)))
                .GET("/chats/-/attachments/{name}", this::attachmentContent, builder -> builder
                        .operationId("getChatAttachmentContent")
                        .description("Download an attachment owned by the current user.")
                        .tag(TAG)
                        .response(responseBuilder()))
                .build();
    }

    private Mono<ServerResponse> complete(ServerRequest request) {
        return command(request).flatMap(chatCompletions::complete)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> stream(ServerRequest request) {
        return command(request).flatMap(command -> {
            var body = chatCompletions.stream(command).map(this::encode);
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Accel-Buffering", "no")
                    .body(body, String.class);
        });
    }

    private Mono<ServerResponse> confirmTool(ServerRequest request) {
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .zipWith(body(request, ToolConfirmation.class))
                .flatMap(tuple -> {
                    var confirmation = tuple.getT2();
                    if (!StringUtils.hasText(confirmation.runId())
                            || !StringUtils.hasText(confirmation.toolCallId())) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                CODE_BODY_INVALID,
                                "runId 与 toolCallId 不能为空。"));
                    }
                    return chatCompletions.confirm(
                                    tuple.getT1(),
                                    confirmation.runId(),
                                    confirmation.toolCallId(),
                                    confirmation.approved())
                            ? ServerResponse.accepted().build()
                            : runNotFound();
                });
    }

    private Mono<ServerResponse> submitExternalResult(ServerRequest request) {
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .zipWith(body(request, ToolExternalResultRequest.class))
                .flatMap(tuple -> {
                    var result = tuple.getT2();
                    if (!StringUtils.hasText(result.runId())
                            || !StringUtils.hasText(result.toolCallId())) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                CODE_BODY_INVALID,
                                "runId 与 toolCallId 不能为空。"));
                    }
                    return chatCompletions.submitExternalResult(
                                    tuple.getT1(),
                                    result.runId(),
                                    result.toolCallId(),
                                    result.output(),
                                    result.error())
                            ? ServerResponse.accepted().build()
                            : runNotFound();
                });
    }

    private Mono<ServerResponse> interruptRun(ServerRequest request) {
        var runId = request.pathVariable("runId");
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .flatMap(userId -> chatCompletions.interrupt(userId, runId)
                        ? ServerResponse.accepted().build()
                        : runNotFound());
    }

    private static Mono<ServerResponse> runNotFound() {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(Map.of(
                        "code", "ai-chat-run-not-found",
                        "detail", "运行不存在或已结束。"));
    }

    private Mono<ServerResponse> uploadAttachment(ServerRequest request) {
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .flatMap(userId -> request.multipartData().flatMap(parts -> {
                    var part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_ATTACHMENT_INVALID, "缺少附件文件。"));
                    }
                    var contentType = filePart.headers().getContentType();
                    var mediaType = contentType == null ? "application/octet-stream" : contentType.toString();
                    if (UNSAFE_TYPES.contains(mediaType)) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                CODE_ATTACHMENT_INVALID,
                                "不支持上传该类型的附件。"));
                    }
                    var fileName = StringUtils.hasText(filePart.filename())
                            ? filePart.filename()
                            : "image";
                    return DataBufferUtils.join(filePart.content(), MAX_ATTACHMENT_BYTES + 1)
                            .switchIfEmpty(Mono.error(new ApiException(
                                    HttpStatus.BAD_REQUEST, CODE_ATTACHMENT_INVALID, "附件内容为空。")))
                            .onErrorMap(
                                    DataBufferLimitException.class,
                                    error -> attachmentTooLarge())
                            .flatMap(buffer -> {
                                var size = buffer.readableByteCount();
                                if (size > MAX_ATTACHMENT_BYTES) {
                                    DataBufferUtils.release(buffer);
                                    return Mono.error(attachmentTooLarge());
                                }
                                var bytes = new byte[size];
                                buffer.read(bytes);
                                DataBufferUtils.release(buffer);
                                return storageTransfer
                                        .upload(new StorageWriteRequest(
                                                null,
                                                userId,
                                                ATTACHMENT_MODULE,
                                                null,
                                                fileName,
                                                mediaType,
                                                size,
                                                Map.of("purpose", "chat-attachment"),
                                                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)),
                                                false))
                                        .map(file -> new AttachmentView(
                                                file.getMetadata().getName(),
                                                file.getSpec().getOriginalName(),
                                                file.getSpec().getContentType(),
                                                file.getSpec().getSize(),
                                                attachmentType(file.getSpec().getContentType())));
                            });
                }))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> attachmentContent(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .flatMap(userId -> storageTransfer
                        .getFile(name)
                        .filter(file -> userId.equals(file.getSpec().getOwner()))
                        .switchIfEmpty(Mono.error(attachmentNotFound()))
                        .filter(file -> file.getSpec().getState() == StorageFileState.ACTIVE)
                        .switchIfEmpty(Mono.error(attachmentNotFound()))
                        .flatMap(file -> storageTransfer
                                .readBytes(name, MAX_ATTACHMENT_BYTES)
                                .flatMap(bytes -> {
                                    var mediaType = contentType(file);
                                    var inline = isInline(mediaType);
                                    return ServerResponse.ok().headers(headers -> {
                                        headers.setContentType(
                                                inline ? mediaType : MediaType.APPLICATION_OCTET_STREAM);
                                        if (!inline) {
                                            headers.setContentDisposition(ContentDisposition.attachment()
                                                    .filename(
                                                            file.getSpec().getOriginalName(),
                                                            StandardCharsets.UTF_8)
                                                    .build());
                                        }
                                    }).bodyValue(bytes);
                                })));
    }

    private static ApiException attachmentTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                CODE_ATTACHMENT_INVALID,
                "附件不能超过 25 MB。");
    }

    private static ApiException attachmentNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "ai-chat-attachment-not-found", "附件不存在。");
    }

    private static MediaType contentType(StorageFile file) {
        var value = file.getSpec().getContentType();
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static boolean isInline(MediaType mediaType) {
        return switch (mediaType.getType()) {
            case "image" -> !"svg+xml".equals(mediaType.getSubtype());
            case "video", "audio" -> true;
            default -> false;
        };
    }

    private static String attachmentType(@Nullable String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "FILE";
        }
        var type = contentType.toLowerCase();
        if (type.startsWith("image/")) {
            return "IMAGE";
        }
        if (type.startsWith("video/")) {
            return "VIDEO";
        }
        if (type.startsWith("audio/")) {
            return "AUDIO";
        }
        if (type.startsWith("text/")
                || type.startsWith("application/pdf")
                || type.startsWith("application/msword")
                || type.startsWith("application/vnd.openxmlformats")
                || type.startsWith("application/vnd.ms-")
                || type.startsWith("application/json")) {
            return "DOCUMENT";
        }
        return "FILE";
    }

    public record AttachmentView(
            String fileId, String name, String contentType, long size, String type) {}

    public record ToolConfirmation(String runId, String toolCallId, boolean approved) {}

    public record ToolExternalResultRequest(
            String runId, String toolCallId, String output, boolean error) {}

    private Mono<AiChatCommand> command(ServerRequest request) {
        return requests
                .userId(request, "ai-chat-unauthenticated", "未认证。")
                .zipWith(body(request))
                .flatMap(tuple -> roles().map(roleNames -> new AiChatCommand(
                        tuple.getT1(), roleNames, null, tuple.getT2())));
    }

    private static Mono<AiChatCompletionRequest> body(ServerRequest request) {
        return body(request, AiChatCompletionRequest.class);
    }

    private static <T> Mono<T> body(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体不能为空。")))
                .onErrorMap(
                        org.springframework.core.codec.DecodingException.class,
                        error -> new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体格式不正确。"))
                .onErrorMap(
                        org.springframework.web.server.ServerWebInputException.class,
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
            case AiChatStreamEvent.RunStarted started -> event("run_started", runStartedJson(started));
            case AiChatStreamEvent.Delta delta -> event("delta", deltaJson(delta));
            case AiChatStreamEvent.CitationEvent citation -> event("citation", citationJson(citation));
            case AiChatStreamEvent.ToolCall toolCall -> event("tool_call", toolCallJson(toolCall));
            case AiChatStreamEvent.ToolResult toolResult -> event("tool_result", toolResultJson(toolResult));
            case AiChatStreamEvent.ToolProgress progress -> event("tool_progress", toolProgressJson(progress));
            case AiChatStreamEvent.ToolResultDelta delta ->
                    event("tool_result_delta", toolResultDeltaJson(delta));
            case AiChatStreamEvent.ToolCompensated compensated ->
                    event("tool_compensated", toolCompensatedJson(compensated));
            case AiChatStreamEvent.DataBlock dataBlock -> event(
                    "data_block_" + dataBlock.phase(), dataBlockJson(dataBlock));
            case AiChatStreamEvent.ToolConfirm toolConfirm -> event("tool_confirm", toolConfirmJson(toolConfirm));
            case AiChatStreamEvent.ToolExternal external -> event("tool_external", toolExternalJson(external));
            case AiChatStreamEvent.ToolExternalResult externalResult ->
                    event("tool_external_result", toolExternalResultJson(externalResult));
            case AiChatStreamEvent.Artifact artifact -> event("artifact", artifactJson(artifact));
            case AiChatStreamEvent.Stage stage -> event("stage", stageJson(stage));
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

    private ObjectNode toolCallJson(AiChatStreamEvent.ToolCall event) {
        var node = mapper.createObjectNode();
        put(node, "id", event.id());
        put(node, "name", event.name());
        put(node, "server", event.server());
        if (event.arguments() != null) {
            node.set("arguments", event.arguments());
        }
        return node;
    }

    private ObjectNode toolResultJson(AiChatStreamEvent.ToolResult event) {
        var node = mapper.createObjectNode();
        put(node, "id", event.id());
        put(node, "name", event.name());
        put(node, "output", event.output());
        node.put("isError", event.isError());
        node.put("durationMs", event.durationMs());
        return node;
    }

    private ObjectNode toolProgressJson(AiChatStreamEvent.ToolProgress event) {
        var node = mapper.createObjectNode();
        put(node, "id", event.id());
        put(node, "message", event.message());
        return node;
    }

    private ObjectNode toolResultDeltaJson(AiChatStreamEvent.ToolResultDelta event) {
        var node = mapper.createObjectNode();
        put(node, "id", event.id());
        put(node, "delta", event.delta());
        return node;
    }

    private ObjectNode toolCompensatedJson(AiChatStreamEvent.ToolCompensated event) {
        var node = mapper.createObjectNode();
        put(node, "id", event.id());
        put(node, "name", event.name());
        put(node, "message", event.message());
        return node;
    }

    private ObjectNode dataBlockJson(AiChatStreamEvent.DataBlock event) {
        var node = mapper.createObjectNode();
        put(node, "mediaType", event.mediaType());
        put(node, "data", event.data());
        return node;
    }

    private ObjectNode runStartedJson(AiChatStreamEvent.RunStarted event) {
        var node = mapper.createObjectNode();
        put(node, "runId", event.runId());
        return node;
    }

    private ObjectNode toolExternalJson(AiChatStreamEvent.ToolExternal event) {
        var node = mapper.createObjectNode();
        put(node, "runId", event.runId());
        put(node, "toolCallId", event.toolCallId());
        put(node, "name", event.name());
        put(node, "arguments", event.arguments());
        return node;
    }

    private ObjectNode toolExternalResultJson(AiChatStreamEvent.ToolExternalResult event) {
        var node = mapper.createObjectNode();
        put(node, "runId", event.runId());
        put(node, "toolCallId", event.toolCallId());
        put(node, "output", event.output());
        node.put("isError", event.error());
        return node;
    }

    private ObjectNode toolConfirmJson(AiChatStreamEvent.ToolConfirm event) {
        var node = mapper.createObjectNode();
        put(node, "runId", event.runId());
        put(node, "toolCallId", event.toolCallId());
        put(node, "name", event.name());
        put(node, "message", event.message());
        return node;
    }

    private ObjectNode artifactJson(AiChatStreamEvent.Artifact event) {
        var node = mapper.createObjectNode();
        put(node, "kind", event.kind());
        put(node, "fileId", event.fileId());
        put(node, "name", event.name());
        put(node, "mimeType", event.mimeType());
        if (event.size() != null) {
            node.put("size", event.size());
        }
        put(node, "url", event.url());
        if (event.progress() != null) {
            node.put("progress", event.progress());
        }
        return node;
    }

    private ObjectNode stageJson(AiChatStreamEvent.Stage event) {
        var node = mapper.createObjectNode();
        put(node, "stage", event.stage());
        put(node, "detail", event.detail());
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
        if (event.units() != null) {
            node.put("units", event.units());
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
