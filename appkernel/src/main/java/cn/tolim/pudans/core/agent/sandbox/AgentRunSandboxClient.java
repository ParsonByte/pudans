package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

@Slf4j
public class AgentRunSandboxClient implements SandboxClient {

    private static final String PROVIDER = "agentrun";

    private static final String TOOL_EXEC = "process_exec_cmd";

    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int SANDBOX_ID_LENGTH = 26;

    private static final int OUTPUT_TRUNCATE_BYTES = 512 * 1024;

    private static final int START_WAIT_SECONDS = 300;

    private static final int TAR_TIMEOUT_SECONDS = 300;

    private static final int MCP_TIMEOUT_SECONDS = 600;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final ExecutorService MCP_CALLS = Executors.newCachedThreadPool(runnable -> {
        var thread = new Thread(runnable, "agentrun-mcp-call");
        thread.setDaemon(true);
        return thread;
    });

    private final SandboxProperties properties;

    private final OkHttpClient http;

    private final ObjectMapper json = JsonUtils.mapper();

    public AgentRunSandboxClient(SandboxProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(properties.normalizedConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.normalizedReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public SandboxSession create(String workspaceRoot, String template, String region) {
        var state = new State(
                sandboxId(),
                normalizeWorkspaceRoot(workspaceRoot),
                StringUtils.hasText(template) ? template.trim() : properties.defaultTemplate(PROVIDER),
                properties.getAccountId(),
                StringUtils.hasText(region) ? region.trim() : properties.getRegion(),
                properties.getMcpServerUrl());
        return new Session(state);
    }

    @Override
    public SandboxSession resume(String providerState) {
        if (!StringUtils.hasText(providerState)) {
            throw new SandboxGateway.Failure(
                    "sandbox-state-missing", "AgentRun 沙箱缺少可恢复状态。", null);
        }
        return new Session(parseState(providerState));
    }

    private State parseState(String providerState) {
        var defaults = new State(
                sandboxId(),
                normalizeWorkspaceRoot(properties.defaultWorkspaceRoot(PROVIDER)),
                properties.defaultTemplate(PROVIDER),
                properties.getAccountId(),
                properties.getRegion(),
                properties.getMcpServerUrl());
        if (!StringUtils.hasText(providerState)) {
            return defaults;
        }
        try {
            var node = json.readTree(providerState);
            return new State(
                    text(node, "sandboxId", defaults.sandboxId()),
                    text(node, "workspaceRoot", defaults.workspaceRoot()),
                    text(node, "templateName", defaults.templateName()),
                    text(node, "accountId", defaults.accountId()),
                    text(node, "region", defaults.region()),
                    text(node, "mcpServerUrl", defaults.mcpServerUrl()));
        } catch (Exception error) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured",
                    "AgentRun 沙箱状态无法解析。",
                    error);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        var value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    static String sandboxId() {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(SANDBOX_ID_LENGTH);
            var bitBuffer = 0;
            var bitsInBuffer = 0;
            var byteIndex = 0;
            while (builder.length() < SANDBOX_ID_LENGTH) {
                if (bitsInBuffer < 5) {
                    bitBuffer = (bitBuffer << 8) | (digest[byteIndex++] & 0xFF);
                    bitsInBuffer += 8;
                }
                var shift = bitsInBuffer - 5;
                builder.append(CROCKFORD32[(bitBuffer >> shift) & 0x1F]);
                bitBuffer &= (1 << shift) - 1;
                bitsInBuffer -= 5;
            }
            return builder.toString();
        } catch (Exception error) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-failed", "无法生成 AgentRun 沙箱标识。", error);
        }
    }

    static String normalizeWorkspaceRoot(String root) {
        if (!StringUtils.hasText(root) || !root.startsWith("/")) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured",
                    "AgentRun 工作区根目录必须是绝对路径。",
                    null);
        }
        return java.nio.file.Path.of(root).normalize().toString().replace('\\', '/');
    }

    private final class Session implements SandboxSession {

        private final State state;

        private volatile DefaultMcpClient mcp;

        private volatile boolean running;

        private Session(State state) {
            this.state = state;
        }

        @Override
        public String providerSandboxId() {
            return state.sandboxId();
        }

        @Override
        public String workspaceRoot() {
            return state.workspaceRoot();
        }

        @Override
        public String providerState() {
            return JsonUtils.objectToJson(state);
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void start() throws Exception {
            ensureSandbox();
            running = true;
            exec("/", "mkdir -p " + shellQuote(state.workspaceRoot()), 30);
        }

        @Override
        public SandboxGateway.Execution exec(String workdir, String command, int timeoutSeconds)
                throws Exception {
            Future<?> future = null;
            try {
                var request = ToolExecutionRequest.builder()
                        .name(TOOL_EXEC)
                        .arguments(execArguments(command, workdir, timeoutSeconds))
                        .build();
                future = MCP_CALLS.submit(() -> mcp().executeTool(request));
                var result = (dev.langchain4j.service.tool.ToolExecutionResult)
                        future.get(Math.max(1, timeoutSeconds) + 30L, TimeUnit.SECONDS);
                if (result.isError()) {
                    var message = StringUtils.hasText(result.resultText())
                            ? result.resultText()
                            : "AgentRun MCP 执行失败。";
                    if (timeoutSeconds > 0 && message.toLowerCase().contains("timeout")) {
                        throw new ExecTimeoutException(message);
                    }
                    throw new IllegalStateException(message);
                }
                return parseExec(result.resultText());
            } catch (ExecTimeoutException error) {
                throw error;
            } catch (TimeoutException error) {
                if (future != null) {
                    future.cancel(true);
                }
                throw new ExecTimeoutException("沙箱命令执行超时。", error);
            } catch (ExecutionException error) {
                var cause = error.getCause() == null ? error : error.getCause();
                if (timeoutSeconds > 0
                        && cause.getMessage() != null
                        && cause.getMessage().toLowerCase().contains("timeout")) {
                    throw new ExecTimeoutException("沙箱命令执行超时。", cause);
                }
                if (cause instanceof Exception checked) {
                    throw checked;
                }
                throw error;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ExecTimeoutException("沙箱命令执行被中断。", error);
            } catch (Exception error) {
                if (timeoutSeconds > 0
                        && error.getMessage() != null
                        && error.getMessage().toLowerCase().contains("timeout")) {
                    throw new ExecTimeoutException("沙箱命令执行超时。", error);
                }
                throw error;
            }
        }

        @Override
        public void stop() {
            running = false;
            closeMcp();
        }

        @Override
        public void shutdown() throws Exception {
            running = false;
            closeMcp();
            if (StringUtils.hasText(state.sandboxId())) {
                deleteSandbox(state.sandboxId());
            }
        }

        @Override
        public InputStream persistWorkspace() throws Exception {
            var execution = exec(
                    null,
                    "tar -cf - -C " + shellQuote(state.workspaceRoot()) + " . | base64 -w0",
                    TAR_TIMEOUT_SECONDS);
            if (execution.exitCode() != 0) {
                throw new IllegalStateException("AgentRun 工作区归档失败：" + execution.stderr());
            }
            if (execution.truncated()) {
                throw new SandboxGateway.Failure(
                        "sandbox-file-output-truncated", "AgentRun 工作区归档输出被截断。", null);
            }
            var encoded = execution.stdout().replace("\n", "").replace("\r", "");
            return new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        }

        private String execArguments(String command, String workdir, int timeoutSeconds) {
            var arguments = new LinkedHashMap<String, Object>();
            arguments.put("command", command);
            if (StringUtils.hasText(workdir)) {
                arguments.put("cwd", workdir);
            }
            arguments.put("timeout", Math.max(1, timeoutSeconds));
            return JsonUtils.objectToJson(arguments);
        }

        private DefaultMcpClient mcp() {
            if (mcp != null) {
                return mcp;
            }
            synchronized (this) {
                if (mcp == null) {
                    var url = resolveMcpUrl(state.mcpServerUrl());
                    var headers = new LinkedHashMap<String, String>();
                    headers.put("X-API-Key", requireApiKey());
                    if (StringUtils.hasText(state.accountId())) {
                        headers.put("X-Acs-Parent-Id", state.accountId());
                    }
                    var timeout = Duration.ofSeconds(MCP_TIMEOUT_SECONDS);
                    var transport = StreamableHttpMcpTransport.builder()
                            .url(url)
                            .customHeaders(Map.copyOf(headers))
                            .timeout(timeout)
                            .build();
                    mcp = DefaultMcpClient.builder()
                            .key("agentrun-" + state.sandboxId())
                            .clientName("pudans-agentrun")
                            .clientVersion("1")
                            .transport(transport)
                            .toolExecutionTimeout(timeout)
                            .build();
                }
                return mcp;
            }
        }

        private void closeMcp() {
            var client = mcp;
            if (client != null) {
                try {
                    client.close();
                } catch (Exception error) {
                    log.warn("关闭 AgentRun MCP 通道失败：{}", error.getMessage());
                }
                mcp = null;
            }
        }

        private void ensureSandbox() throws Exception {
            var id = state.sandboxId();
            if (!StringUtils.hasText(id)) {
                throw new SandboxGateway.Failure(
                        "sandbox-state-missing", "AgentRun 沙箱标识缺失。", null);
            }
            if (sandboxExists(id)) {
                waitUntilReady(id, 30);
                return;
            }
            createSandbox(id);
            waitUntilReady(id, START_WAIT_SECONDS);
        }

        private boolean sandboxExists(String sandboxId) throws Exception {
            return SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                var request = baseRequest(url("/sandboxes/" + sandboxId, state), state).get().build();
                try (Response response = http.newCall(request).execute()) {
                    if (response.code() == 404) {
                        return false;
                    }
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException(
                                "AgentRun HTTP " + response.code() + " " + response.message());
                    }
                    return true;
                }
            });
        }

        private void createSandbox(String sandboxId) throws Exception {
            var body = new LinkedHashMap<String, Object>();
            body.put("sandboxId", sandboxId);
            body.put("templateName", state.templateName());
            body.put("sandboxIdleTimeoutSeconds", properties.sandboxIdleTimeoutSeconds());
            var request = baseRequest(url("/sandboxes", state), state)
                    .post(RequestBody.create(JsonUtils.objectToJson(body), JSON))
                    .build();
            SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                try (Response response = http.newCall(request).execute()) {
                    if (response.isSuccessful() || response.code() == 409) {
                        return true;
                    }
                    var text = response.body() == null ? "" : response.body().string();
                    throw new IllegalStateException(
                            "创建 AgentRun 沙箱失败：HTTP " + response.code() + " " + text);
                }
            });
        }

        private void deleteSandbox(String sandboxId) throws Exception {
            SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                var request = baseRequest(url("/sandboxes/" + sandboxId, state), state).delete().build();
                try (Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful() && response.code() != 404) {
                        throw new IllegalStateException(
                                "AgentRun 删除沙箱失败：HTTP " + response.code());
                    }
                    return true;
                }
            });
        }

        private void waitUntilReady(String sandboxId, int maxWaitSeconds) throws Exception {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(maxWaitSeconds);
            String last = null;
            while (System.nanoTime() < deadline) {
                last = SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                    var request = baseRequest(url("/sandboxes/" + sandboxId, state), state).get().build();
                    try (Response response = http.newCall(request).execute()) {
                        var text = response.body() == null ? "" : response.body().string();
                        if (!response.isSuccessful()) {
                            throw new IllegalStateException(
                                    "AgentRun HTTP " + response.code() + " " + response.message());
                        }
                        return status(text);
                    }
                });
                if (last != null) {
                    var upper = last.toUpperCase();
                    if (upper.contains("READY") || upper.contains("RUNNING")) {
                        return;
                    }
                    if (upper.contains("FAILED") || upper.contains("TERMINATED")) {
                        throw new IllegalStateException(
                                "AgentRun 沙箱进入终态 " + last + "：" + sandboxId);
                    }
                }
                Thread.sleep(1_500L);
            }
            throw new IllegalStateException(
                    "AgentRun 沙箱未在时限内就绪（最后状态=" + last + "）：" + sandboxId);
        }
    }

    private String status(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            var node = json.readTree(body);
            var status = node.path("status").asText("");
            if (StringUtils.hasText(status)) {
                return status;
            }
            var state = node.path("state").asText("");
            return StringUtils.hasText(state) ? state : null;
        } catch (Exception error) {
            return null;
        }
    }

    private String url(String path, State state) {
        return dataPlaneBaseUrl(state) + path;
    }

    private String dataPlaneBaseUrl(State state) {
        if (StringUtils.hasText(properties.getDataPlaneBaseUrl())) {
            return stripTrailingSlash(properties.getDataPlaneBaseUrl().trim());
        }
        var accountId = StringUtils.hasText(state.accountId())
                ? state.accountId().trim()
                : properties.getAccountId();
        var region = StringUtils.hasText(state.region())
                ? state.region().trim()
                : properties.getRegion();
        if (StringUtils.hasText(accountId) && StringUtils.hasText(region)) {
            return "https://" + accountId + ".agentrun-data." + region + ".aliyuncs.com";
        }
        throw new SandboxGateway.Failure(
                "sandbox-provider-not-configured",
                "AgentRun 需要账号与地域或显式数据面地址。",
                null);
    }

    private Request.Builder baseRequest(String url, State state) {
        var builder = new Request.Builder().url(url).addHeader("X-API-Key", requireApiKey());
        var accountId = StringUtils.hasText(state.accountId())
                ? state.accountId()
                : properties.getAccountId();
        if (StringUtils.hasText(accountId)) {
            builder.addHeader("X-Acs-Parent-Id", accountId);
        }
        return builder;
    }

    private String requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured", "AgentRun API Key 尚未配置。", null);
        }
        return properties.getApiKey().trim();
    }

    private static String resolveMcpUrl(String base) {
        if (!StringUtils.hasText(base)) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured", "AgentRun MCP 地址尚未配置。", null);
        }
        var trimmed = base.trim();
        if (trimmed.endsWith("/mcp") || trimmed.contains("/mcp?")) {
            return trimmed;
        }
        return stripTrailingSlash(trimmed) + "/mcp";
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static SandboxGateway.Execution parseExec(String text) {
        if (!StringUtils.hasText(text)) {
            return new SandboxGateway.Execution(0, "", "", false);
        }
        var trimmed = text.strip();
        var brace = trimmed.indexOf('{');
        if (brace >= 0) {
            try {
                var node = JsonUtils.mapper().readTree(trimmed.substring(brace));
                var result = node.path("result");
                var source = result.isObject() ? result : node;
                if (source.has("exitCode") || source.has("exit_code")) {
                    var exit = source.has("exitCode")
                            ? source.path("exitCode").asInt(0)
                            : source.path("exit_code").asInt(0);
                    var stdout = source.has("stdout")
                            ? source.path("stdout").asText("")
                            : source.path("output").asText("");
                    var stderr = source.path("stderr").asText("");
                    return limit(exit, stdout, stderr);
                }
            } catch (Exception ignored) {
                // 供应商可能返回纯文本输出，继续按纯文本处理。
            }
        }
        return limit(0, trimmed, "");
    }

    private static SandboxGateway.Execution limit(int exit, String stdout, String stderr) {
        var out = stdout == null ? "" : stdout;
        var err = stderr == null ? "" : stderr;
        var truncated = out.length() >= OUTPUT_TRUNCATE_BYTES || err.length() >= OUTPUT_TRUNCATE_BYTES;
        return new SandboxGateway.Execution(
                exit,
                out.length() >= OUTPUT_TRUNCATE_BYTES
                        ? out.substring(0, OUTPUT_TRUNCATE_BYTES)
                        : out,
                err.length() >= OUTPUT_TRUNCATE_BYTES
                        ? err.substring(0, OUTPUT_TRUNCATE_BYTES)
                        : err,
                truncated);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private record State(
            String sandboxId,
            String workspaceRoot,
            String templateName,
            String accountId,
            String region,
            String mcpServerUrl) {

        private State {
            Objects.requireNonNull(sandboxId, "sandboxId");
            Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        }
    }
}