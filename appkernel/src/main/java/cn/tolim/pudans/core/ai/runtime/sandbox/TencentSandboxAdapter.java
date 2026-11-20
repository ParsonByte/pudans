package cn.tolim.pudans.core.ai.runtime.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 腾讯云 Agent Runtime 适配：控制面 AGS OpenAPI，数据面使用 E2B 兼容 envd 协议。
public final class TencentSandboxAdapter implements SandboxProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(TencentSandboxAdapter.class);

    private static final String NAME = "TENCENT";

    private static final String ACTION_START = "StartSandboxInstance";

    private static final String ACTION_STOP = "StopSandboxInstance";

    private static final String ACTION_DESCRIBE = "DescribeSandboxInstanceList";

    private static final String ACTION_TOKEN = "AcquireSandboxInstanceToken";

    private static final int ENTRYPOINT_TIMEOUT_SECONDS = 30;

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final SandboxProviderConfig config;

    private final SandboxRuntimeSettings settings;

    public TencentSandboxAdapter(
            WebClient webClient,
            ObjectMapper mapper,
            SandboxProviderConfig config,
            SandboxRuntimeSettings settings) {
        this.webClient = webClient;
        this.mapper = mapper;
        this.config = config;
        this.settings = settings;
    }

    @Override
    public Mono<SandboxHandle> create(SandboxCreateSpec spec) {
        var tool = config.defaultImage();
        if (!StringUtils.hasText(tool)) {
            return Mono.error(SandboxErrors.configInvalid("TENCENT 提供方未配置 defaultImage（ToolId 或 ToolName）。"));
        }
        var effectiveTimeout = Math.min(spec.timeoutSeconds(), config.maxTimeoutSeconds());
        var payload = new LinkedHashMap<String, Object>();
        if (tool.startsWith("sdt-")) {
            payload.put("ToolId", tool);
        } else {
            payload.put("ToolName", tool);
        }
        payload.put("Timeout", effectiveTimeout + "s");
        payload.put("AuthMode", "TOKEN");
        payload.put("NetworkMode", spec.networkEnabled() ? "PUBLIC" : "SANDBOX");
        var custom = customConfiguration(spec);
        if (!custom.isEmpty()) {
            payload.put("CustomConfiguration", custom);
        }
        return invokeControl(ACTION_START, payload).flatMap(response -> {
            var instance = response.get("Instance");
            var sandboxId = instance == null ? null : SandboxSupport.text(instance, "InstanceId");
            if (!StringUtils.hasText(sandboxId)) {
                return Mono.error(SandboxErrors.providerResponse(
                        NAME, "创建", new IllegalStateException("响应缺少 InstanceId")));
            }
            return startEntrypoint(sandboxId, spec)
                    .thenReturn(new SandboxHandle(sandboxId, dataEndpoint(sandboxId)))
                    .onErrorResume(error -> stopQuietly(sandboxId).then(Mono.error(error)));
        });
    }

    @Override
    public Mono<Void> destroy(String sandboxId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("InstanceId", sandboxId);
        return invokeControl(ACTION_STOP, payload)
                .then()
                .onErrorResume(error -> isNotFound(error) ? Mono.empty() : Mono.error(error));
    }

    @Override
    public Mono<SandboxExecResult> exec(String sandboxId, SandboxExecSpec spec) {
        return token(sandboxId)
                .flatMap(token -> execConnect(token, dataEndpoint(sandboxId), spec))
                .onErrorMap(error -> SandboxErrors.execFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxFile> readFile(String sandboxId, String path) {
        return token(sandboxId)
                .flatMap(token -> webClient
                        .get()
                        .uri(fileUri(sandboxId, path))
                        .accept(MediaType.ALL)
                        .header("X-Access-Token", token)
                        .exchangeToMono(response -> response.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .flatMap(body -> response.statusCode().isError()
                                        ? Mono.error(SandboxErrors.providerRejected(
                                                NAME, "读取文件", "HTTP_" + response.statusCode().value(), null))
                                        : Mono.just(body)))
                        .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds())))
                .map(content -> SandboxFiles.fromBytes(path, content))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<Void> writeFile(String sandboxId, String path, byte[] content) {
        var resource = new ByteArrayResource(content == null ? new byte[0] : content) {
            @Override
            public String getFilename() {
                return fileName(path);
            }
        };
        var multipart = new org.springframework.http.client.MultipartBodyBuilder();
        multipart.part("file", resource).contentType(MediaType.APPLICATION_OCTET_STREAM);
        return token(sandboxId)
                .flatMap(token -> webClient
                        .post()
                        .uri(fileUri(sandboxId, path))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("X-Access-Token", token)
                        .body(BodyInserters.fromMultipartData(multipart.build()))
                        .exchangeToMono(response -> {
                            if (response.statusCode().isError()) {
                                return response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> Mono.error(SandboxErrors.providerRejected(
                                                NAME, "写入文件", "HTTP_" + response.statusCode().value(), null)));
                            }
                            return response.bodyToMono(Void.class);
                        })
                        .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds())))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxStatus> status(String sandboxId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("InstanceIds", List.of(sandboxId));
        return invokeControl(ACTION_DESCRIBE, payload)
                .map(response -> {
                    var instances = response.get("InstanceSet");
                    if (instances == null || !instances.isArray() || instances.isEmpty()) {
                        return new SandboxStatus(SandboxState.DESTROYED, "NOT_FOUND");
                    }
                    var status = SandboxSupport.text(instances.get(0), "Status");
                    return new SandboxStatus(mapState(status), status);
                })
                .onErrorResume(error -> isNotFound(error)
                        ? Mono.just(new SandboxStatus(SandboxState.DESTROYED, "NOT_FOUND"))
                        : Mono.error(error));
    }

    @Override
    public Flux<SandboxLogEvent> logs(String sandboxId, @Nullable String cursor) {
        var offset = parseCursor(cursor);
        var command = "tail -c +" + (offset + 1) + " " + SandboxSupport.quote(settings.logFile()) + " 2>/dev/null || true";
        var spec = new SandboxExecSpec(command, List.of(), null, Map.of(), ENTRYPOINT_TIMEOUT_SECONDS);
        return token(sandboxId)
                .flatMapMany(token -> execConnect(token, dataEndpoint(sandboxId), spec)
                        .flatMapMany(result -> Flux.fromIterable(logEvents(result.stdout()))))
                .onErrorMap(error -> SandboxErrors.logFailed(sandboxId, error));
    }

    private Mono<Void> startEntrypoint(String sandboxId, SandboxCreateSpec spec) {
        var entrypoint = spec.entrypoint();
        if (!StringUtils.hasText(entrypoint)) {
            return Mono.empty();
        }
        var command = "nohup " + entrypoint + " >> " + SandboxSupport.quote(settings.logFile()) + " 2>&1 &";
        var execSpec = new SandboxExecSpec(
                command, List.of(), spec.workdir(), Map.of(), ENTRYPOINT_TIMEOUT_SECONDS);
        return token(sandboxId)
                .flatMap(token -> execConnect(token, dataEndpoint(sandboxId), execSpec))
                .flatMap(result -> result.exitCode() == 0
                        ? Mono.empty()
                        : Mono.error(SandboxErrors.createFailed(
                                spec.agentName(), new IllegalStateException("entrypoint 启动失败"))));
    }

    private Map<String, Object> customConfiguration(SandboxCreateSpec spec) {
        var custom = new LinkedHashMap<String, Object>();
        if (StringUtils.hasText(spec.image())) {
            custom.put("Image", spec.image());
        }
        if (!spec.environment().isEmpty()) {
            var environment = new TreeMap<>(spec.environment());
            var values = new ArrayList<Map<String, Object>>();
            environment.forEach((name, value) -> values.add(Map.of(
                    "Name", name,
                    "Value", value == null ? "" : value)));
            custom.put("Env", values);
        }
        var resources = new LinkedHashMap<String, Object>();
        resources.put("CPU", formatCpu(spec.cpu()));
        resources.put("Memory", spec.memoryMb() + "Mi");
        custom.put("Resources", resources);
        return custom;
    }

    private Mono<SandboxExecResult> execConnect(String token, String endpoint, SandboxExecSpec spec) {
        var started = System.nanoTime();
        var process = new LinkedHashMap<String, Object>();
        process.put("cmd", "/bin/bash");
        process.put("args", List.of("-l", "-c", SandboxSupport.shellCommand(spec.command(), spec.args())));
        if (!spec.environment().isEmpty()) {
            process.put("envs", spec.environment());
        }
        if (StringUtils.hasText(spec.workdir())) {
            process.put("cwd", spec.workdir());
        }
        var message = new LinkedHashMap<String, Object>();
        message.put("process", process);
        message.put("stdin", false);
        var request = ConnectStreamCodec.frame(
                SandboxSupport.writeJson(mapper, message, NAME, "执行命令").getBytes(StandardCharsets.UTF_8));
        return webClient
                .post()
                .uri(endpoint + "/process.Process/Start")
                .contentType(MediaType.parseMediaType("application/connect+json"))
                .header("Connect-Protocol-Version", "1")
                .header("Connect-Timeout-Ms", Integer.toString(spec.timeoutSeconds() * 1000))
                .header("Keepalive-Ping-Interval", "50")
                .header("X-Access-Token", token)
                .bodyValue(request)
                .exchangeToMono(response -> response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> {
                            if (!response.statusCode().isError()) {
                                return Mono.just(body);
                            }
                            var error = connectError(body, response.statusCode().value());
                            return Mono.error(SandboxErrors.providerRejected(
                                    NAME, "执行命令", error.code(), error.message()));
                        }))
                .timeout(Duration.ofSeconds((long) spec.timeoutSeconds() + settings.requestTimeoutSeconds()))
                .map(body -> processResult(body, started));
    }

    private SandboxExecResult processResult(byte[] body, long startedNanos) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        Integer exitCode = null;
        String error = null;
        try {
            for (var envelope : ConnectStreamCodec.unframe(body)) {
                if (envelope.endStream()) {
                    var end = errorPayload(envelope.payload());
                    if (end != null) {
                        throw SandboxErrors.providerRejected(NAME, "执行命令", end.code(), end.message());
                    }
                    continue;
                }
                var node = SandboxSupport.readJson(
                        mapper, new String(envelope.payload(), StandardCharsets.UTF_8), NAME, "执行命令");
                var event = node.get("event");
                if (event == null || !event.isObject()) {
                    continue;
                }
                var data = event.get("data");
                if (data != null && data.isObject()) {
                    writeBytes(stdout, data.get("stdout"));
                    writeBytes(stderr, data.get("stderr"));
                }
                var end = event.get("end");
                if (end != null && end.isObject()) {
                    exitCode = SandboxSupport.integer(end, "exitCode", 0);
                    var endError = SandboxSupport.text(end, "error");
                    if (StringUtils.hasText(endError)) {
                        error = endError;
                    }
                }
            }
        } catch (IllegalArgumentException errorEnvelope) {
            throw SandboxErrors.providerResponse(NAME, "执行命令", errorEnvelope);
        }
        if (exitCode == null) {
            throw SandboxErrors.providerResponse(
                    NAME, "执行命令", new IllegalStateException("响应缺少结束事件"));
        }
        if (StringUtils.hasText(error) && stderr.size() == 0) {
            stderr.writeBytes(error.getBytes(StandardCharsets.UTF_8));
        }
        var durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new SandboxExecResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                durationMs);
    }

    private Mono<JsonNode> invokeControl(String action, Map<String, Object> payload) {
        var baseUrl = baseUrl();
        var host = URI.create(baseUrl).getAuthority();
        var body = SandboxSupport.writeJson(mapper, payload, NAME, action);
        var timestamp = Instant.now().getEpochSecond();
        var authorization = TencentCloudSignature.authorization(
                SandboxSupport.requireText(config.accessKeyId(), "TENCENT accessKeyId"),
                config.accessKeySecret(),
                action,
                host,
                timestamp,
                body);
        return webClient
                .post()
                .uri(baseUrl + "/")
                .contentType(MediaType.parseMediaType(TencentCloudSignature.contentType()))
                .header("X-TC-Action", action)
                .header("X-TC-Version", settings.tencentApiVersion())
                .header("X-TC-Timestamp", Long.toString(timestamp))
                .header("X-TC-Region", config.region())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(text -> {
                            if (response.statusCode().isError()) {
                                var error = controlError(text);
                                return Mono.error(SandboxErrors.providerRejected(
                                        NAME, action, error.code(), error.message()));
                            }
                            return Mono.just(text);
                        }))
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                .flatMap(text -> validateControl(text, action))
                .onErrorMap(error -> SandboxErrors.providerFailure(NAME, action, error));
    }

    private Mono<JsonNode> validateControl(String text, String action) {
        var node = SandboxSupport.readJson(mapper, text, NAME, action);
        var response = node.get("Response");
        if (response == null || !response.isObject()) {
            return Mono.error(SandboxErrors.providerResponse(
                    NAME, action, new IllegalStateException("响应缺少 Response")));
        }
        var error = response.get("Error");
        if (error != null && error.isObject()) {
            return Mono.error(SandboxErrors.providerRejected(
                    NAME,
                    action,
                    SandboxSupport.text(error, "Code"),
                    SandboxSupport.text(error, "Message")));
        }
        return Mono.just(response);
    }

    private Mono<String> token(String sandboxId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("InstanceId", sandboxId);
        return invokeControl(ACTION_TOKEN, payload)
                .map(response -> SandboxSupport.text(response, "Token"))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(SandboxErrors.providerResponse(
                        NAME, "获取访问令牌", new IllegalStateException("响应缺少 Token"))));
    }

    private URI fileUri(String sandboxId, String path) {
        return UriComponentsBuilder.fromUriString(dataEndpoint(sandboxId))
                .path("/files")
                .queryParam("path", path)
                .build()
                .encode()
                .toUri();
    }

    private String dataEndpoint(String sandboxId) {
        var region = SandboxSupport.requireRegion(config.region(), NAME);
        return settings.tencentDataPlaneTemplate().formatted(sandboxId, region);
    }

    private String baseUrl() {
        var defaultUrl = "https://ags." + SandboxSupport.requireRegion(config.region(), NAME) + ".tencentcloudapi.com";
        return SandboxSupport.baseUrl(config.endpoint(), defaultUrl, "TENCENT endpoint");
    }

    private ConnectError connectError(byte[] body, int status) {
        try {
            for (var envelope : ConnectStreamCodec.unframe(body)) {
                if (!envelope.endStream()) {
                    continue;
                }
                var error = errorPayload(envelope.payload());
                if (error != null) {
                    return error;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // 非信封响应按 HTTP 状态处理。
        }
        return new ConnectError("HTTP_" + status, null);
    }

    private ConnectError errorPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            var node = mapper.readTree(payload);
            var error = node == null ? null : node.get("error");
            if (error == null || !error.isObject()) {
                return null;
            }
            return new ConnectError(
                    SandboxSupport.text(error, "code"), SandboxSupport.text(error, "message"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ControlError controlError(String body) {
        if (StringUtils.hasText(body)) {
            try {
                var node = mapper.readTree(body);
                var response = node == null ? null : node.get("Response");
                var error = response == null ? null : response.get("Error");
                if (error != null && error.isObject()) {
                    return new ControlError(
                            SandboxSupport.text(error, "Code"), SandboxSupport.text(error, "Message"));
                }
            } catch (Exception ignored) {
                // 不回显原始响应。
            }
        }
        return new ControlError(null, null);
    }

    private void writeBytes(ByteArrayOutputStream target, @Nullable JsonNode encoded) {
        if (encoded == null || !encoded.isTextual() || encoded.textValue().isEmpty()) {
            return;
        }
        var value = encoded.textValue();
        try {
            target.writeBytes(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException error) {
            target.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<SandboxLogEvent> logEvents(@Nullable String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        var events = new ArrayList<SandboxLogEvent>();
        var lines = content.split("\\R", -1);
        var limit = lines.length;
        if (limit > 0 && lines[limit - 1].isEmpty()) {
            limit--;
        }
        var now = Instant.now();
        for (var index = 0; index < limit; index++) {
            events.add(new SandboxLogEvent(now, "stdout", lines[index]));
        }
        return events;
    }

    private Mono<Void> stopQuietly(String sandboxId) {
        return destroy(sandboxId).onErrorResume(error -> {
            log.warn("TENCENT 沙箱 {} 创建失败后的清理也失败。", sandboxId);
            return Mono.empty();
        });
    }

    private static String fileName(String path) {
        try {
            var name = Path.of(path).getFileName();
            return name == null ? "file" : name.toString();
        } catch (RuntimeException error) {
            return "file";
        }
    }

    private static String formatCpu(double cpu) {
        if (cpu == Math.rint(cpu)) {
            return Long.toString((long) cpu);
        }
        return Math.round(cpu * 1000) + "m";
    }

    private static long parseCursor(@Nullable String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(cursor.strip()));
        } catch (NumberFormatException error) {
            throw SandboxErrors.configInvalid("日志游标必须是字节偏移量。");
        }
    }

    private static SandboxState mapState(@Nullable String status) {
        if (status == null) {
            return SandboxState.FAILED;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "STARTING" -> SandboxState.CREATING;
            case "RUNNING", "RESIZING" -> SandboxState.RUNNING;
            case "STOPPING" -> SandboxState.STOPPING;
            case "STOPPED", "STOP_FAILED" -> SandboxState.STOPPED;
            case "FAILED" -> SandboxState.FAILED;
            default -> SandboxState.FAILED;
        };
    }

    private static boolean isNotFound(Throwable error) {
        var message = error instanceof cn.tolim.pudans.infra.exception.ApiException apiException
                ? apiException.getReason()
                : error.getMessage();
        return message != null
                && (message.toLowerCase(Locale.ROOT).contains("notfound")
                        || message.toLowerCase(Locale.ROOT).contains("not found"));
    }

    private record ConnectError(String code, String message) {}

    private record ControlError(String code, String message) {}
}