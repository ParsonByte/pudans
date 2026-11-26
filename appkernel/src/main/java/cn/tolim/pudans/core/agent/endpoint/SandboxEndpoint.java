package cn.tolim.pudans.core.agent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.contract.agent.dto.SandboxCreateRequest;
import cn.tolim.pudans.contract.agent.dto.SandboxEvent;
import cn.tolim.pudans.contract.agent.dto.SandboxExecRequest;
import cn.tolim.pudans.contract.agent.dto.SandboxExecResult;
import cn.tolim.pudans.contract.agent.dto.SandboxFileResult;
import cn.tolim.pudans.contract.agent.dto.SandboxOperationPage;
import cn.tolim.pudans.contract.agent.dto.SandboxPage;
import cn.tolim.pudans.core.agent.sandbox.SandboxAudit;
import cn.tolim.pudans.core.agent.sandbox.SandboxEvents;
import cn.tolim.pudans.core.agent.sandbox.SandboxProperties;
import cn.tolim.pudans.core.agent.sandbox.Sandboxes;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SandboxEndpoint implements CustomEndpoint {

    private static final String TAG = "SandboxV1alpha1";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private final Sandboxes sandboxes;

    private final SandboxEvents events;

    private final ReactiveRequestIdentity identity;

    private final SandboxAudit audits;

    private final SandboxProperties properties;

    // 注册沙箱创建、查询、执行、快照、停止、事件和删除路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/sandboxes", this::create, builder -> builder
                        .operationId("createSandbox")
                        .description("Create one sandbox for the current owner and workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(SandboxCreateRequest.class))))
                        .response(responseBuilder().implementation(Sandbox.class)))
                .POST("/sandboxes/-/create", this::create, builder -> builder
                        .operationId("createSandboxAlias")
                        .description("Create one sandbox using the contract path alias.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(SandboxCreateRequest.class))))
                        .response(responseBuilder().implementation(Sandbox.class)))
                .GET("/sandboxes", this::list, builder -> builder
                        .operationId("listSandboxes")
                        .description("List sandboxes owned by the current user and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxPage.class)))
                .GET("/sandboxes/-/mine", this::list, builder -> builder
                        .operationId("listSandboxesAlias")
                        .description("List sandboxes using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxPage.class)))
                .GET("/sandboxes/-/all", this::all, builder -> builder
                        .operationId("listAllSandboxes")
                        .description("List sandboxes across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxPage.class)))
                .GET("/sandboxes/{name}", this::view, builder -> builder
                        .operationId("getSandbox")
                        .description("Get one sandbox owned by the current user and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Sandbox.class)))
                .GET("/sandboxes/{name}/view", this::view, builder -> builder
                        .operationId("getSandboxAlias")
                        .description("Get one sandbox using the contract path alias.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Sandbox.class)))
                .POST("/sandboxes/{name}/-/exec", this::exec, builder -> builder
                        .operationId("execSandboxCommand")
                        .description("Execute one low-risk command inside the sandbox workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(SandboxExecRequest.class))))
                        .response(responseBuilder().implementation(SandboxExecResult.class)))
                .GET("/sandboxes/{name}/operations", this::operations, builder -> builder
                        .operationId("listSandboxOperations")
                        .description("List sandbox operations with a stable started-at cursor.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxOperationPage.class)))
                .GET("/sandboxes/{name}/operations/{operationName}", this::operation, builder -> builder
                        .operationId("getSandboxOperation")
                        .description("Get one sandbox operation owned by the current user.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxOperation.class)))
                .POST("/sandboxes/{name}/-/resume", this::resume, builder -> builder
                        .operationId("resumeSandbox")
                        .description("Resume one stopped sandbox using its persisted provider state.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Sandbox.class)))
                .POST("/sandboxes/{name}/-/upload", this::upload, builder -> builder
                        .operationId("uploadSandboxFile")
                        .description("Upload one bounded file into the sandbox workspace.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder().mediaType(MediaType.MULTIPART_FORM_DATA_VALUE)))
                        .response(responseBuilder().implementation(SandboxFileResult.class)))
                .GET("/sandboxes/{name}/-/download", this::download, builder -> builder
                        .operationId("downloadSandboxFile")
                        .description("Download one file from the sandbox workspace.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_OCTET_STREAM_VALUE))))
                .POST("/sandboxes/{name}/-/snapshot", this::snapshot, builder -> builder
                        .operationId("createSandboxSnapshot")
                        .description("Archive the sandbox workspace through the configured snapshot route.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(SandboxSnapshot.class)))
                .POST("/sandboxes/{name}/-/stop", this::stop, builder -> builder
                        .operationId("stopSandbox")
                        .description("Stop the provider sandbox while preserving its recoverable state.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Sandbox.class)))
                .GET("/sandboxes/{name}/events", this::sandboxEvents, builder -> builder
                        .operationId("streamSandboxEvents")
                        .description("Stream sandbox events with Last-Event-ID replay and keepalive pings.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .content(contentBuilder()
                                        .mediaType(MediaType.TEXT_EVENT_STREAM_VALUE)
                                        .schema(schemaBuilder().implementation(SandboxEvent.class)))))
                .DELETE("/sandboxes/{name}", this::delete, builder -> builder
                        .operationId("deleteSandbox")
                        .description("Destroy the provider sandbox and keep its terminal audit record.")
                        .tag(TAG)
                        .response(responseBuilder().description("Sandbox deleted.")))
                .build();
    }

    // 携带幂等键创建真实沙箱，重复请求返回同一资源。
    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(SandboxCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity
                        .resolve(request)
                        .flatMap(owner -> sandboxes.create(
                                owner,
                                body,
                                request.headers().firstHeader(IDEMPOTENCY_HEADER))))
                .flatMap(result -> {
                    var extra = new LinkedHashMap<String, Object>();
                    extra.put("replayed", !result.created());
                    return audits
                            .record(
                                    request,
                                    "CREATE_SANDBOX",
                                    result.sandbox(),
                                    true,
                                    Map.copyOf(extra))
                            .thenReturn(result);
                })
                .flatMap(result -> ServerResponse.status(
                                result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .bodyValue(result.sandbox()));
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return Mono.defer(() -> {
            var cursor = CreationCursor.decode(
                    request.queryParam("cursor").orElse(null),
                    "sandbox-cursor-invalid");
            var limit = limit(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes.list(owner, cursor, limit))
                    .flatMap(page -> ServerResponse.ok().bodyValue(page));
        });
    }

    private Mono<ServerResponse> all(ServerRequest request) {
        return Mono.defer(() -> {
            var cursor = CreationCursor.decode(
                    request.queryParam("cursor").orElse(null),
                    "sandbox-cursor-invalid");
            return sandboxes
                    .listAll(cursor, limit(request))
                    .flatMap(page -> ServerResponse.ok().bodyValue(page));
        });
    }

    private Mono<ServerResponse> view(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes.view(owner, sandboxId))
                    .flatMap(sandbox -> ServerResponse.ok().bodyValue(sandbox));
        });
    }

    // 先读取归属沙箱用于审计，再执行命令并记录真实结果摘要。
    private Mono<ServerResponse> exec(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return request.bodyToMono(SandboxExecRequest.class)
                    .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                    .flatMap(body -> identity.resolve(request).flatMap(owner -> sandboxes
                            .view(owner, sandboxId)
                            .flatMap(sandbox -> sandboxes
                                    .exec(owner, sandboxId, body)
                                    .flatMap(result -> audits
                                            .record(
                                                    request,
                                                    "EXEC_SANDBOX",
                                                    sandbox,
                                                    true,
                                                    execContext(result))
                                            .thenReturn(result))
                                    .onErrorResume(error -> auditFailure(
                                                    request, "EXEC_SANDBOX", sandboxId, error)
                                            .then(Mono.error(error))))))
                    .flatMap(result -> ServerResponse.ok().bodyValue(result));
        });
    }

    private Mono<ServerResponse> operations(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            var cursor = CreationCursor.decode(
                    request.queryParam("cursor").orElse(null),
                    "sandbox-cursor-invalid");
            var limit = limit(request);
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes.operations(owner, sandboxId, cursor, limit))
                    .flatMap(page -> ServerResponse.ok().bodyValue(page));
        });
    }

    private Mono<ServerResponse> operation(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            var operationId = uuid(
                    request, "operationName", "sandbox-operation-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes.operation(owner, sandboxId, operationId))
                    .flatMap(result -> ServerResponse.ok().bodyValue(result));
        });
    }

    // 恢复操作必须由已停止状态进入，失败时服务层写入失败事实。
    private Mono<ServerResponse> resume(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes
                            .view(owner, sandboxId)
                            .flatMap(sandbox -> sandboxes
                                    .resume(owner, sandboxId)
                                    .flatMap(resumed -> audits
                                            .record(
                                                    request,
                                                    "RESUME_SANDBOX",
                                                    sandbox,
                                                    true,
                                                    Map.of("status", resumed.status().getCode()))
                                            .thenReturn(resumed))
                                    .onErrorResume(error -> auditFailure(
                                                    request, "RESUME_SANDBOX", sandboxId, error)
                                            .then(Mono.error(error)))))
                    .flatMap(sandbox -> ServerResponse.ok().bodyValue(sandbox));
        });
    }

    // multipart 只接受 path 文本字段和 file 文件字段，并在读取时限制内存占用。
    private Mono<ServerResponse> upload(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return request.multipartData()
                    .flatMap(parts -> {
                        var pathPart = parts.getFirst("path");
                        if (!(pathPart instanceof FormFieldPart path)
                                || !StringUtils.hasText(path.value())) {
                            return Mono.error(new ServerWebInputException(
                                    "需要名为 path 的 multipart 文本字段。"));
                        }
                        var filePart = parts.getFirst("file");
                        if (!(filePart instanceof FilePart file)) {
                            return Mono.error(new ServerWebInputException(
                                    "需要名为 file 的 multipart 文件字段。"));
                        }
                        return readUpload(file).flatMap(content -> identity
                                .resolve(request)
                                .flatMap(owner -> sandboxes
                                        .view(owner, sandboxId)
                                        .flatMap(sandbox -> sandboxes
                                                .upload(owner, sandboxId, path.value(), content)
                                                .flatMap(result -> audits
                                                        .record(
                                                                request,
                                                                "UPLOAD_SANDBOX_FILE",
                                                                sandbox,
                                                                true,
                                                                fileContext(result))
                                                        .thenReturn(result))
                                                .onErrorResume(error -> auditFailure(
                                                                request,
                                                                "UPLOAD_SANDBOX_FILE",
                                                                sandboxId,
                                                                error)
                                                        .then(Mono.error(error))))));
                    })
                    .flatMap(result -> ServerResponse.status(HttpStatus.CREATED).bodyValue(result));
        });
    }

    // 下载只返回文件流，不把文件内容写入审计或日志。
    private Mono<ServerResponse> download(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            var path = request.queryParam("path").orElse(null);
            if (!StringUtils.hasText(path)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, "sandbox-path-required", "文件路径不能为空。"));
            }
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes
                            .view(owner, sandboxId)
                            .flatMap(sandbox -> sandboxes
                                    .download(owner, sandboxId, path)
                                    .flatMap(result -> audits
                                            .record(
                                                    request,
                                                    "DOWNLOAD_SANDBOX_FILE",
                                                    sandbox,
                                                    true,
                                                    downloadContext(result))
                                            .thenReturn(result))
                                    .onErrorResume(error -> auditFailure(
                                                    request,
                                                    "DOWNLOAD_SANDBOX_FILE",
                                                    sandboxId,
                                                    error)
                                            .then(Mono.error(error)))))
                    .flatMap(result -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header(
                                    HttpHeaders.CONTENT_DISPOSITION,
                                    ContentDisposition.attachment()
                                            .filename(result.fileName(), StandardCharsets.UTF_8)
                                            .build()
                                            .toString())
                            .contentLength(result.content().length)
                            .bodyValue(result.content()));
        });
    }

    // 快照必须成功写入显式沙箱快照路由后才返回创建结果。
    private Mono<ServerResponse> snapshot(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes
                            .view(owner, sandboxId)
                            .flatMap(sandbox -> sandboxes
                                    .snapshot(owner, sandboxId)
                                    .flatMap(snapshot -> audits
                                            .record(
                                                    request,
                                                    "SNAPSHOT_SANDBOX",
                                                    sandbox,
                                                    true,
                                                    snapshotContext(snapshot))
                                            .thenReturn(snapshot))
                                    .onErrorResume(error -> auditFailure(
                                                    request, "SNAPSHOT_SANDBOX", sandboxId, error)
                                            .then(Mono.error(error)))))
                    .flatMap(snapshot -> ServerResponse.status(HttpStatus.CREATED).bodyValue(snapshot));
        });
    }

    private Mono<ServerResponse> stop(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes
                            .stop(owner, sandboxId)
                            .flatMap(sandbox -> audits
                                    .record(
                                            request,
                                            "STOP_SANDBOX",
                                            sandbox,
                                            true,
                                            Map.of("status", sandbox.status().getCode()))
                                    .thenReturn(sandbox))
                            .onErrorResume(error -> auditFailure(
                                            request, "STOP_SANDBOX", sandboxId, error)
                                    .then(Mono.error(error))))
                    .flatMap(sandbox -> ServerResponse.ok().bodyValue(sandbox));
        });
    }

    // 先校验事件游标，再解析归属并重放沙箱事件。
    private Mono<ServerResponse> sandboxEvents(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            var lastEventId = request.headers().firstHeader(LAST_EVENT_ID_HEADER);
            if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "sandbox-event-id-invalid",
                        "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
            }
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes.view(owner, sandboxId))
                    .flatMap(sandbox -> ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .header("Cache-Control", "no-cache")
                            .header("X-Accel-Buffering", "no")
                            .body(
                                    events.stream(sandbox, lastEventId)
                                            .map(SandboxEndpoint::serverSentEvent),
                                    ServerSentEvent.class));
        });
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return Mono.defer(() -> {
            var sandboxId = uuid(request, "sandbox-id-invalid");
            return identity
                    .resolve(request)
                    .flatMap(owner -> sandboxes
                            .view(owner, sandboxId)
                            .flatMap(sandbox -> sandboxes
                                    .delete(owner, sandboxId)
                                    .then(audits.record(
                                            request,
                                            "DELETE_SANDBOX",
                                            sandbox,
                                            true,
                                            Map.of("deleted", true)))
                                    .onErrorResume(error -> auditFailure(
                                                    request, "DELETE_SANDBOX", sandboxId, error)
                                            .then(Mono.error(error)))
                                    .then(ServerResponse.noContent().build())));
        });
    }

    private Mono<Void> auditFailure(
            ServerRequest request, String operation, UUID sandboxId, Throwable error) {
        return audits.failure(
                request, operation, sandboxId.toString(), errorCode(error));
    }

    private static Map<String, Object> execContext(SandboxExecResult result) {
        var context = new LinkedHashMap<String, Object>();
        context.put("operationId", result.operationId());
        context.put("status", result.status().getCode());
        if (result.exitCode() != null) {
            context.put("exitCode", result.exitCode());
        }
        context.put("truncated", result.outputTruncated());
        return Map.copyOf(context);
    }

    private Mono<byte[]> readUpload(FilePart file) {
        var maxBytes = properties.normalizedMaxFileBytes();
        var declaredLength = file.headers().getContentLength();
        if (declaredLength > maxBytes) {
            return Mono.error(fileTooLarge(maxBytes));
        }
        return DataBufferUtils.join(file.content(), maxBytes + 1)
                .map(buffer -> toBytes(buffer, maxBytes))
                .onErrorMap(
                        DataBufferLimitException.class,
                        error -> fileTooLarge(maxBytes))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "sandbox-file-empty",
                        "上传文件不能为空。")));
    }

    private static byte[] toBytes(DataBuffer buffer, int maxBytes) {
        try {
            if (buffer.readableByteCount() > maxBytes) {
                throw fileTooLarge(maxBytes);
            }
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static ApiException fileTooLarge(int maxBytes) {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "sandbox-file-too-large",
                "文件大小不能超过 " + maxBytes + " 字节。");
    }

    private static Map<String, Object> fileContext(SandboxFileResult result) {
        return Map.of(
                "path", result.path(),
                "sizeBytes", result.sizeBytes(),
                "sha256", result.sha256());
    }

    private static Map<String, Object> downloadContext(Sandboxes.DownloadResult result) {
        return Map.of(
                "fileName", result.fileName(),
                "sizeBytes", result.content().length,
                "sha256", result.sha256());
    }

    private static Map<String, Object> snapshotContext(SandboxSnapshot snapshot) {
        var context = new LinkedHashMap<String, Object>();
        context.put("snapshotId", snapshot.snapshotId());
        context.put("storagePolicyId", snapshot.storagePolicyId());
        if (snapshot.sizeBytes() != null) {
            context.put("sizeBytes", snapshot.sizeBytes());
        }
        return Map.copyOf(context);
    }

    private static String errorCode(Throwable error) {
        return error instanceof ApiException api
                ? api.getCode()
                : "sandbox-operation-failed";
    }

    private static ServerSentEvent<SandboxEvent> serverSentEvent(SandboxEvent event) {
        var builder = ServerSentEvent.<SandboxEvent>builder()
                .event(event.type())
                .data(event);
        if (AiEventStreams.validId(event.eventId())) {
            builder.id(event.eventId());
        }
        return builder.build();
    }

    private static UUID uuid(ServerRequest request, String code) {
        return uuid(request, "name", code);
    }

    private static UUID uuid(ServerRequest request, String variable, String code) {
        try {
            return UUID.fromString(request.pathVariable(variable));
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "资源标识不是有效 UUID。");
        }
    }

    private static int limit(ServerRequest request) {
        var raw = request.queryParam("limit").orElse(null);
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-limit-invalid",
                    "查询参数 'limit' 必须是整数。");
        }
    }
}
