package cn.tolim.pudans.core.ai.runtime.sandbox;

import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// E2B 沙箱适配：控制面使用 E2B REST API，数据面使用 envd Connect 协议
public final class E2BSandboxAdapter implements SandboxProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(E2BSandboxAdapter.class);

    private static final String NAME = "E2B";

    private static final String DEFAULT_ENDPOINT = "https://api.e2b.dev";

    private static final String ENVD_PORT = "49983";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final SandboxProviderConfig config;

    private final SandboxRuntimeSettings settings;

    private final EnvdSandboxClient envd;

    public E2BSandboxAdapter(
            WebClient webClient,
            ObjectMapper mapper,
            SandboxProviderConfig config,
            SandboxRuntimeSettings settings) {
        this.webClient = webClient;
        this.mapper = mapper;
        this.config = config;
        this.settings = settings;
        this.envd = new EnvdSandboxClient(
                webClient, mapper, NAME, settings.requestTimeoutSeconds());
    }

    @Override
    public Mono<SandboxHandle> create(SandboxCreateSpec spec) {
        var template = config.defaultImage();
        if (!StringUtils.hasText(template)) {
            return Mono.error(SandboxErrors.configInvalid("E2B 提供方未配置 defaultImage（模板 ID）。"));
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("templateID", template);
        payload.put("timeout", Math.min(spec.timeoutSeconds(), config.maxTimeoutSeconds()));
        payload.put("metadata", Map.of("agent", spec.agentName()));
        return control(HttpMethod.POST, "/sandboxes", "创建", payload).flatMap(response -> {
            var sandboxId = firstText(response, "sandboxID", "sandboxId", "id");
            if (!StringUtils.hasText(sandboxId)) {
                return Mono.error(SandboxErrors.providerResponse(
                        NAME, "创建", new IllegalStateException("响应缺少 sandboxID")));
            }
            return connect(sandboxId)
                    .flatMap(connection -> envd.startEntrypoint(
                                    connection.endpoint(), connection.token(), spec, settings.logFile())
                            .thenReturn(new SandboxHandle(sandboxId, connection.endpoint()))
                            .onErrorResume(error -> destroyQuietly(sandboxId).then(Mono.error(error))));
        });
    }

    @Override
    public Mono<Void> destroy(String sandboxId) {
        return control(HttpMethod.DELETE, "/sandboxes/" + sandboxId, "销毁", null)
                .then()
                .onErrorResume(error -> isNotFound(error) ? Mono.empty() : Mono.error(error));
    }

    @Override
    public Mono<SandboxExecResult> exec(String sandboxId, SandboxExecSpec spec) {
        return connect(sandboxId)
                .flatMap(connection -> envd.exec(connection.endpoint(), connection.token(), spec))
                .onErrorMap(error -> SandboxErrors.execFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxFile> readFile(String sandboxId, String path) {
        return connect(sandboxId)
                .flatMap(connection -> envd.readFile(connection.endpoint(), connection.token(), sandboxId, path))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<Void> writeFile(String sandboxId, String path, byte[] content) {
        return connect(sandboxId)
                .flatMap(connection -> envd.writeFile(
                        connection.endpoint(), connection.token(), sandboxId, path, content))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxStatus> status(String sandboxId) {
        return control(HttpMethod.GET, "/sandboxes/" + sandboxId, "状态", null)
                .map(response -> {
                    var state = firstText(response, "state", "status");
                    return new SandboxStatus(mapState(state), state);
                })
                .onErrorResume(error -> isNotFound(error)
                        ? Mono.just(new SandboxStatus(SandboxState.DESTROYED, "NOT_FOUND"))
                        : Mono.error(error));
    }

    @Override
    public Flux<SandboxLogEvent> logs(String sandboxId, @Nullable String cursor) {
        return connect(sandboxId)
                .flatMapMany(connection -> envd.logs(
                        connection.endpoint(), connection.token(), settings.logFile(), cursor))
                .onErrorMap(error -> SandboxErrors.logFailed(sandboxId, error));
    }

    private Mono<Connection> connect(String sandboxId) {
        return control(HttpMethod.POST, "/sandboxes/" + sandboxId + "/connect", "连接", null)
                .map(response -> {
                    var token = firstText(response, "envdAccessToken", "accessToken", "token");
                    var domain = firstText(response, "domain", "sandboxDomain");
                    if (!StringUtils.hasText(token)) {
                        throw SandboxErrors.providerResponse(
                                NAME, "连接", new IllegalStateException("响应缺少 envdAccessToken"));
                    }
                    if (!StringUtils.hasText(domain)) {
                        throw SandboxErrors.providerResponse(
                                NAME, "连接", new IllegalStateException("响应缺少 domain"));
                    }
                    return new Connection(token, endpoint(domain, sandboxId));
                });
    }

    private static String endpoint(String domain, String sandboxId) {
        var host = domain.contains("://") ? domain.substring(domain.indexOf("://") + 3) : domain;
        return "https://" + ENVD_PORT + "-" + sandboxId + "." + host;
    }

    private Mono<JsonNode> control(
            HttpMethod method, String path, String action, @Nullable Map<String, Object> payload) {
        var request = webClient
                .method(method)
                .uri(baseUrl() + path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", apiKey());
        var body = payload == null ? null : SandboxSupport.writeJson(mapper, payload, NAME, action);
        return (body == null ? request : request.bodyValue(body))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(text -> {
                            if (response.statusCode().isError()) {
                                return Mono.error(providerError(text, response.statusCode().value()));
                            }
                            return Mono.just(text);
                        }))
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                .map(text -> {
                    if (!StringUtils.hasText(text)) {
                        return SandboxSupport.readJson(mapper, "{}", NAME, action);
                    }
                    return SandboxSupport.readJson(mapper, text, NAME, action);
                })
                .onErrorMap(
                        error -> error instanceof ApiException
                                ? error
                                : SandboxErrors.providerFailure(NAME, action, error));
    }

    private ApiException providerError(String text, int status) {
        String code = "HTTP_" + status;
        String message = null;
        if (StringUtils.hasText(text)) {
            try {
                var node = mapper.readTree(text);
                var error = node == null ? null : node.get("error");
                if (error != null && error.isObject()) {
                    code = firstText(error, "code", "name");
                    message = firstText(error, "message");
                }
            } catch (Exception ignored) {
                // 不回显原始响应。
            }
        }
        return SandboxErrors.providerRejected(NAME, "调用", code, message);
    }

    private String apiKey() {
        var key = config.accessKeySecret();
        if (!StringUtils.hasText(key)) {
            throw SandboxErrors.configInvalid("E2B 提供方未配置 accessKeySecret（API Key）。");
        }
        return key;
    }

    private String baseUrl() {
        var value = StringUtils.hasText(config.endpoint()) ? config.endpoint() : DEFAULT_ENDPOINT;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Mono<Void> destroyQuietly(String sandboxId) {
        return destroy(sandboxId).onErrorResume(error -> {
            log.warn("E2B 沙箱 {} 创建失败后的清理也失败。", sandboxId);
            return Mono.empty();
        });
    }

    private static @Nullable String firstText(JsonNode node, String... names) {
        for (var name : names) {
            var value = SandboxSupport.text(node, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static SandboxState mapState(@Nullable String status) {
        if (status == null) {
            return SandboxState.FAILED;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RUNNING" -> SandboxState.RUNNING;
            case "PAUSED", "STOPPED" -> SandboxState.STOPPED;
            case "STOPPING", "KILLING" -> SandboxState.STOPPING;
            case "KILLED", "TERMINATED" -> SandboxState.DESTROYED;
            default -> SandboxState.FAILED;
        };
    }

    private static boolean isNotFound(Throwable error) {
        if (error instanceof ApiException apiException) {
            return containsNotFound(apiException.getCode()) || containsNotFound(apiException.getReason());
        }
        return containsNotFound(error.getMessage());
    }

    private static boolean containsNotFound(@Nullable String value) {
        if (value == null) {
            return false;
        }
        var normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("not found")
                || normalized.contains("notfound")
                || normalized.contains("404");
    }

    private record Connection(String token, String endpoint) {}
}