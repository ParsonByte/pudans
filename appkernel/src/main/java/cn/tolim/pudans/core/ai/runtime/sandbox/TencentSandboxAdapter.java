package cn.tolim.pudans.core.ai.runtime.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
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

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final SandboxProviderConfig config;

    private final SandboxRuntimeSettings settings;

    private final EnvdSandboxClient envd;

    public TencentSandboxAdapter(
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
                .flatMap(token -> envd.exec(dataEndpoint(sandboxId), token, spec))
                .onErrorMap(error -> SandboxErrors.execFailed(sandboxId, error));
    }

    @Override
    public Mono<SandboxFile> readFile(String sandboxId, String path) {
        return token(sandboxId)
                .flatMap(token -> envd.readFile(dataEndpoint(sandboxId), token, sandboxId, path))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    @Override
    public Mono<Void> writeFile(String sandboxId, String path, byte[] content) {
        return token(sandboxId)
                .flatMap(token -> envd.writeFile(dataEndpoint(sandboxId), token, sandboxId, path, content))
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
        return token(sandboxId)
                .flatMapMany(token -> envd.logs(
                        dataEndpoint(sandboxId), token, settings.logFile(), cursor))
                .onErrorMap(error -> SandboxErrors.logFailed(sandboxId, error));
    }

    private Mono<Void> startEntrypoint(String sandboxId, SandboxCreateSpec spec) {
        if (!StringUtils.hasText(spec.entrypoint())) {
            return Mono.empty();
        }
        return token(sandboxId)
                .flatMap(token -> envd.startEntrypoint(
                        dataEndpoint(sandboxId), token, spec, settings.logFile()));
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

    private String dataEndpoint(String sandboxId) {
        var region = SandboxSupport.requireRegion(config.region(), NAME);
        return settings.tencentDataPlaneTemplate().formatted(sandboxId, region);
    }

    private String baseUrl() {
        var defaultUrl = "https://ags." + SandboxSupport.requireRegion(config.region(), NAME) + ".tencentcloudapi.com";
        return SandboxSupport.baseUrl(config.endpoint(), defaultUrl, "TENCENT endpoint");
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

    private Mono<Void> stopQuietly(String sandboxId) {
        return destroy(sandboxId).onErrorResume(error -> {
            log.warn("TENCENT 沙箱 {} 创建失败后的清理也失败。", sandboxId);
            return Mono.empty();
        });
    }

    private static String formatCpu(double cpu) {
        if (cpu == Math.rint(cpu)) {
            return Long.toString((long) cpu);
        }
        return Math.round(cpu * 1000) + "m";
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

    private record ControlError(String code, String message) {}
}