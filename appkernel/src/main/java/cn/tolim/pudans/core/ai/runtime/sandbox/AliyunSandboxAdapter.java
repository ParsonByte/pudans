package cn.tolim.pudans.core.ai.runtime.sandbox;

import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 阿里云 AgentBay 适配：控制面使用 OpenAPI RPC，沙箱内命令与文件操作使用 MCP 工具。
public final class AliyunSandboxAdapter implements SandboxProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AliyunSandboxAdapter.class);

    private static final String NAME = "ALIYUN";

    private static final String API_VERSION = "2025-05-06";

    private static final String ACTION_CREATE = "CreateMcpSession";

    private static final String ACTION_CALL_TOOL = "CallMcpTool";

    private static final String ACTION_DETAIL = "GetSessionDetail";

    private static final String ACTION_DELETE = "DeleteSessionAsync";

    private static final String ENV_FILE = ".pudans-runtime.env";

    private static final int ENTRYPOINT_TIMEOUT_MS = 30000;

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final SandboxProviderConfig config;

    private final SandboxRuntimeSettings settings;

    public AliyunSandboxAdapter(
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
        var image = firstText(spec.image(), config.defaultImage());
        if (!StringUtils.hasText(image)) {
            return Mono.error(SandboxErrors.configInvalid(
                    "ALIYUN 提供方未配置 defaultImage，Agent 也未配置 runtime.image。"));
        }
        var effectiveTimeout = Math.min(spec.timeoutSeconds(), config.maxTimeoutSeconds());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("Authorization", bearer());
        payload.put("ImageId", image);
        payload.put("Timeout", effectiveTimeout);
        payload.put("MaxRuntime", effectiveTimeout);
        payload.put("ManualRelease", true);
        return invokeAction(ACTION_CREATE, payload).flatMap(body -> {
            var data = data(body, ACTION_CREATE);
            var sessionId = SandboxSupport.text(data, "SessionId");
            if (!StringUtils.hasText(sessionId)) {
                return Mono.error(SandboxErrors.providerResponse(
                        NAME, "创建", new IllegalStateException("响应缺少 SessionId")));
            }
            var linkUrl = SandboxSupport.text(data, "LinkUrl");
            return injectEnvironment(sessionId, spec)
                    .then(startEntrypoint(sessionId, spec))
                    .thenReturn(new SandboxHandle(sessionId, linkUrl))
                    .onErrorResume(error -> destroyQuietly(sessionId).then(Mono.error(error)));
        });
    }

    @Override
    public Mono<Void> destroy(String sandboxId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("Authorization", bearer());
        payload.put("SessionId", sandboxId);
        return invokeAction(ACTION_DELETE, payload)
                .then()
                .onErrorResume(error -> isNotFound(error) ? Mono.empty() : Mono.error(error));
    }

    @Override
    public Mono<SandboxExecResult> exec(String sandboxId, SandboxExecSpec spec) {
        var started = System.nanoTime();
        var args = new LinkedHashMap<String, Object>();
        args.put("command", execCommand(spec));
        args.put("timeout_ms", spec.timeoutSeconds() * 1000);
        if (StringUtils.hasText(spec.workdir())) {
            args.put("cwd", spec.workdir());
        }
        if (!spec.environment().isEmpty()) {
            args.put("envs", spec.environment());
        }
        return callTool(sandboxId, "shell", args)
                .map(result -> shellResult(result, started))
                .onErrorMap(error -> SandboxErrors.execFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxFile> readFile(String sandboxId, String path) {
        var args = new LinkedHashMap<String, Object>();
        args.put("path", path);
        args.put("offset", 0);
        args.put("length", 0);
        args.put("format", "binary");
        return callTool(sandboxId, "read_file", args)
                .flatMap(result -> {
                    if (!result.success()) {
                        return Mono.error(SandboxErrors.fileFailed(sandboxId, new IllegalStateException(result.message())));
                    }
                    var content = result.data();
                    var bytes = content == null ? new byte[0] : decodeFile(content);
                    return Mono.just(SandboxFiles.fromBytes(path, bytes));
                })
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<Void> writeFile(String sandboxId, String path, byte[] content) {
        var text = SandboxFiles.utf8Text(content);
        if (text == null) {
            return Mono.error(SandboxErrors.configInvalid("ALIYUN 沙箱写入仅支持 UTF-8 文本文件。"));
        }
        var args = new LinkedHashMap<String, Object>();
        args.put("path", path);
        args.put("content", text);
        args.put("mode", "overwrite");
        return callTool(sandboxId, "write_file", args)
                .flatMap(result -> result.success()
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(SandboxErrors.fileFailed(
                                sandboxId, new IllegalStateException(result.message()))))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxStatus> status(String sandboxId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("Authorization", bearer());
        payload.put("SessionId", sandboxId);
        return invokeAction(ACTION_DETAIL, payload)
                .map(body -> {
                    var status = SandboxSupport.text(data(body, ACTION_DETAIL), "Status");
                    return new SandboxStatus(mapState(status), status);
                })
                .onErrorResume(error -> isNotFound(error)
                        ? Mono.just(new SandboxStatus(SandboxState.DESTROYED, "NOT_FOUND"))
                        : Mono.error(error));
    }

    @Override
    public Flux<SandboxLogEvent> logs(String sandboxId, @Nullable String cursor) {
        var offset = parseCursor(cursor);
        var args = new LinkedHashMap<String, Object>();
        args.put("command", "tail -c +" + (offset + 1) + " " + SandboxSupport.quote(settings.logFile()) + " 2>/dev/null || true");
        args.put("timeout_ms", settings.requestTimeoutSeconds() * 1000);
        return callTool(sandboxId, "shell", args)
                .flatMapMany(result -> Flux.fromIterable(logEvents(result.data())))
                .onErrorMap(error -> SandboxErrors.logFailed(sandboxId, error));
    }

    private Mono<Void> injectEnvironment(String sessionId, SandboxCreateSpec spec) {
        if (spec.environment().isEmpty()) {
            return Mono.empty();
        }
        var args = new LinkedHashMap<String, Object>();
        args.put("path", envFile(spec.workdir()));
        args.put("content", envContent(spec.environment()));
        args.put("mode", "overwrite");
        return callTool(sessionId, "write_file", args).flatMap(result -> result.success()
                ? Mono.empty()
                : Mono.error(SandboxErrors.createFailed(
                        spec.agentName(), new IllegalStateException(result.message()))));
    }

    private Mono<Void> startEntrypoint(String sessionId, SandboxCreateSpec spec) {
        var entrypoint = spec.entrypoint();
        if (!StringUtils.hasText(entrypoint)) {
            return Mono.empty();
        }
        var command = "nohup " + entrypoint + " >> " + SandboxSupport.quote(settings.logFile()) + " 2>&1 &";
        var args = new LinkedHashMap<String, Object>();
        args.put("command", command);
        args.put("timeout_ms", ENTRYPOINT_TIMEOUT_MS);
        if (StringUtils.hasText(spec.workdir())) {
            args.put("cwd", spec.workdir());
        }
        return callTool(sessionId, "shell", args).flatMap(result -> result.success()
                ? Mono.empty()
                : Mono.error(SandboxErrors.createFailed(
                        spec.agentName(), new IllegalStateException(result.message()))));
    }

    private String execCommand(SandboxExecSpec spec) {
        var envFile = envFile(spec.workdir());
        return "set -a; if [ -f " + SandboxSupport.quote(envFile) + " ]; then . "
                + SandboxSupport.quote(envFile) + "; fi; set +a; exec "
                + SandboxSupport.shellCommand(spec.command(), spec.args());
    }

    private Mono<ToolResult> callTool(String sandboxId, String toolName, Map<String, Object> args) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("Authorization", bearer());
        payload.put("SessionId", sandboxId);
        payload.put("Name", toolName);
        payload.put("Args", SandboxSupport.writeJson(mapper, args, NAME, toolName));
        return invokeAction(ACTION_CALL_TOOL, payload).map(body -> toolResult(body, toolName));
    }

    private Mono<JsonNode> invokeAction(String action, Map<String, Object> payload) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl())
                .queryParam("Action", action)
                .queryParam("Version", API_VERSION)
                .build()
                .toUri();
        var body = SandboxSupport.writeJson(mapper, payload, NAME, action);
        return webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(text -> {
                            if (response.statusCode().isError()) {
                                var error = providerError(text);
                                return Mono.error(SandboxErrors.providerRejected(
                                        NAME, action, error.code(), error.message()));
                            }
                            return Mono.just(text);
                        }))
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds() * 2L))
                .flatMap(text -> validateBody(text, action))
                .onErrorMap(error -> SandboxErrors.providerFailure(NAME, action, error));
    }

    private Mono<JsonNode> validateBody(String text, String action) {
        var body = SandboxSupport.readJson(mapper, text, NAME, action);
        var success = body.get("Success");
        var code = SandboxSupport.text(body, "Code");
        var message = SandboxSupport.text(body, "Message");
        if (success != null && success.isBoolean() && !success.asBoolean()) {
            return Mono.error(SandboxErrors.providerRejected(NAME, action, code, message));
        }
        if (StringUtils.hasText(code) && !"200".equals(code) && !"Success".equalsIgnoreCase(code)) {
            return Mono.error(SandboxErrors.providerRejected(NAME, action, code, message));
        }
        return Mono.just(body);
    }

    private ToolResult toolResult(JsonNode body, String toolName) {
        var data = body.get("Data");
        var message = SandboxSupport.text(body, "Message");
        var success = body.get("Success");
        var ok = success == null || !success.isBoolean() || success.asBoolean();
        if (data == null || data.isNull()) {
            return new ToolResult(ok, "", message);
        }
        if (data.isTextual()) {
            var text = data.textValue();
            if (StringUtils.hasText(text) && text.stripLeading().startsWith("{")) {
                try {
                    var parsed = mapper.readTree(text);
                    var innerSuccess = parsed.get("success");
                    if (innerSuccess != null && innerSuccess.isBoolean()) {
                        ok = ok && innerSuccess.asBoolean();
                    }
                } catch (Exception error) {
                    log.debug("ALIYUN 工具 {} 的 Data 不是 JSON。", toolName);
                }
            }
            return new ToolResult(ok, text, message);
        }
        return new ToolResult(ok, data.toString(), message);
    }

    private JsonNode data(JsonNode body, String action) {
        var data = body.get("Data");
        if (data == null || data.isNull()) {
            throw SandboxErrors.providerResponse(NAME, action, new IllegalStateException("响应缺少 Data"));
        }
        if (data.isTextual()) {
            return SandboxSupport.readJson(mapper, data.textValue(), NAME, action);
        }
        return data;
    }

    private SandboxExecResult shellResult(ToolResult result, long startedNanos) {
        var payload = parseShellPayload(result);
        var durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        return new SandboxExecResult(payload.exitCode(), payload.stdout(), payload.stderr(), durationMs);
    }

    private ShellPayload parseShellPayload(ToolResult result) {
        var raw = result.data();
        if (StringUtils.hasText(raw)) {
            try {
                var node = mapper.readTree(raw);
                if (node != null && node.isObject() && (node.has("exit_code") || node.has("stdout") || node.has("stderr"))) {
                    var exitCode = SandboxSupport.integer(node, "exit_code", 0);
                    var stdout = SandboxSupport.text(node, "stdout");
                    var stderr = SandboxSupport.text(node, "stderr");
                    return new ShellPayload(exitCode, stdout == null ? "" : stdout, stderr == null ? "" : stderr);
                }
            } catch (Exception ignored) {
                // 非包装输出按原始 stdout 处理。
            }
        }
        if (!result.success()) {
            var message = StringUtils.hasText(result.message()) ? result.message() : raw;
            return new ShellPayload(1, "", message == null ? "" : message);
        }
        return new ShellPayload(0, raw == null ? "" : raw, "");
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

    private byte[] decodeFile(String content) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException error) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private Mono<Void> destroyQuietly(String sandboxId) {
        return destroy(sandboxId).onErrorResume(error -> {
            log.warn("ALIYUN 沙箱 {} 创建失败后的清理也失败。", sandboxId);
            return Mono.empty();
        });
    }

    private String baseUrl() {
        var defaultUrl = "https://agentbay." + SandboxSupport.requireRegion(config.region(), NAME) + ".aliyuncs.com";
        return SandboxSupport.baseUrl(config.endpoint(), defaultUrl, "ALIYUN endpoint");
    }

    private String bearer() {
        return "Bearer " + config.accessKeySecret();
    }

    private String envFile(String workdir) {
        var base = workdir.endsWith("/") ? workdir.substring(0, workdir.length() - 1) : workdir;
        return base + "/" + ENV_FILE;
    }

    private static String envContent(Map<String, String> environment) {
        var builder = new StringBuilder();
        environment.forEach((name, value) -> builder.append("export ")
                .append(name)
                .append("=")
                .append(SandboxSupport.quote(value == null ? "" : value))
                .append('\n'));
        return builder.toString();
    }

    private static String firstText(@Nullable String first, @Nullable String second) {
        return StringUtils.hasText(first) ? first : second;
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
        return switch (status.toUpperCase()) {
            case "RUNNING" -> SandboxState.RUNNING;
            case "PAUSED", "FINISH" -> SandboxState.STOPPED;
            case "DELETING" -> SandboxState.STOPPING;
            case "DELETED" -> SandboxState.DESTROYED;
            default -> SandboxState.FAILED;
        };
    }

    private static boolean isNotFound(Throwable error) {
        if (error instanceof ApiException apiException) {
            var message = apiException.getReason();
            var code = apiException.getCode();
            return containsNotFound(code) || containsNotFound(message);
        }
        return containsNotFound(error.getMessage());
    }

    private static boolean containsNotFound(@Nullable String value) {
        if (value == null) {
            return false;
        }
        var normalized = value.toLowerCase();
        return normalized.contains("notfound") || normalized.contains("not found");
    }

    private ProviderError providerError(String body) {
        if (StringUtils.hasText(body)) {
            try {
                var node = mapper.readTree(body);
                return new ProviderError(SandboxSupport.text(node, "Code"), SandboxSupport.text(node, "Message"));
            } catch (Exception ignored) {
                // 响应不是 JSON 时不回显原始内容。
            }
        }
        return new ProviderError(null, null);
    }

    private record ToolResult(boolean success, String data, String message) {}

    private record ShellPayload(int exitCode, String stdout, String stderr) {}

    private record ProviderError(String code, String message) {}
}