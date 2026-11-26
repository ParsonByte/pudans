package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.util.StringUtils;

@Slf4j
public class E2bSandboxClient implements SandboxClient {

    private static final String PROVIDER = "e2b";

    private static final MediaType CONNECT_JSON = MediaType.get("application/connect+json");

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final int ENVD_PORT = 49983;

    private static final int OUTPUT_TRUNCATE_BYTES = 512 * 1024;

    private static final int TAR_TIMEOUT_SECONDS = 300;

    private static final long CLIENT_TIMEOUT_SLACK_MILLIS = 5_000;

    private static final String CONNECT_DEADLINE_EXCEEDED = "deadline_exceeded";

    private final SandboxProperties properties;

    private final OkHttpClient http;

    private final ObjectMapper json = JsonUtils.mapper();

    public E2bSandboxClient(SandboxProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(properties.normalizedConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.normalizedReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public SandboxSession create(String workspaceRoot, String template, String region) {
        var e2b = properties.getE2b();
        var state = new State(
                null,
                normalizeWorkspaceRoot(workspaceRoot),
                StringUtils.hasText(template) ? template.trim() : properties.defaultTemplate(PROVIDER),
                e2b.getDomain().trim(),
                null);
        return new Session(state);
    }

    @Override
    public SandboxSession resume(String providerState) {
        if (!StringUtils.hasText(providerState)) {
            throw new SandboxGateway.Failure(
                    "sandbox-state-missing", "E2B 沙箱缺少可恢复状态。", null);
        }
        return new Session(parseState(providerState));
    }

    private State parseState(String providerState) {
        var e2b = properties.getE2b();
        var defaults = new State(
                null,
                normalizeWorkspaceRoot(properties.defaultWorkspaceRoot(PROVIDER)),
                properties.defaultTemplate(PROVIDER),
                e2b.getDomain().trim(),
                null);
        if (!StringUtils.hasText(providerState)) {
            return defaults;
        }
        try {
            var node = json.readTree(providerState);
            return new State(
                    text(node, "sandboxId", null),
                    text(node, "workspaceRoot", defaults.workspaceRoot()),
                    text(node, "templateId", defaults.templateId()),
                    text(node, "sandboxDomain", text(node, "domain", defaults.domain())),
                    text(node, "envdAccessToken", null));
        } catch (Exception error) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured",
                    "E2B 沙箱状态无法解析。",
                    error);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        var value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : fallback;
    }

    static String normalizeWorkspaceRoot(String root) {
        if (!StringUtils.hasText(root) || !root.startsWith("/")) {
            throw new SandboxGateway.Failure(
                    "sandbox-provider-not-configured",
                    "E2B 工作区根目录必须是沙箱内的绝对路径。",
                    null);
        }
        return java.nio.file.Path.of(root).normalize().toString().replace('\\', '/');
    }

    private final class Session implements SandboxSession {

        private volatile String sandboxId;

        private volatile String workspaceRoot;

        private volatile String templateId;

        private volatile String domain;

        private volatile String envdAccessToken;

        private volatile boolean running;

        private Session(State state) {
            this.sandboxId = state.sandboxId();
            this.workspaceRoot = state.workspaceRoot();
            this.templateId = state.templateId();
            this.domain = state.domain();
            this.envdAccessToken = state.envdAccessToken();
        }

        @Override
        public String providerSandboxId() {
            return sandboxId;
        }

        @Override
        public String workspaceRoot() {
            return workspaceRoot;
        }

        @Override
        public String providerState() {
            return JsonUtils.objectToJson(new State(
                    sandboxId, workspaceRoot, templateId, domain, envdAccessToken));
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void start() throws Exception {
            requireTarPersistence();
            if (StringUtils.hasText(sandboxId) && connectSandbox(sandboxId)) {
                running = true;
                exec("/", "mkdir -p " + shellQuote(workspaceRoot), 30);
                return;
            }
            createSandbox();
            running = true;
            exec("/", "mkdir -p " + shellQuote(workspaceRoot), 30);
        }

        @Override
        public SandboxGateway.Execution exec(String workdir, String command, int timeoutSeconds)
                throws Exception {
            if (!StringUtils.hasText(sandboxId)) {
                throw new IllegalStateException("E2B 沙箱尚未创建。");
            }
            var cwd = StringUtils.hasText(workdir) ? workdir : workspaceRoot;
            return runShell(cwd, command, timeoutSeconds);
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public void shutdown() throws Exception {
            running = false;
            if (!StringUtils.hasText(sandboxId)) {
                return;
            }
            var request = new Request.Builder()
                    .url(apiBaseUrl() + "/sandboxes/" + sandboxId)
                    .addHeader("X-API-Key", requireApiKey())
                    .delete()
                    .build();
            SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                try (Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful() && response.code() != 404) {
                        throw new IllegalStateException(
                                "E2B 删除沙箱失败：HTTP " + response.code());
                    }
                    return true;
                }
            });
            sandboxId = null;
        }

        @Override
        public InputStream persistWorkspace() throws Exception {
            requireTarPersistence();
            var execution = exec(
                    null,
                    "tar -cf - -C " + shellQuote(workspaceRoot) + " . | base64 | tr -d '\\n'",
                    TAR_TIMEOUT_SECONDS);
            if (execution.exitCode() != 0) {
                throw new IllegalStateException("E2B 工作区归档失败：" + execution.stderr());
            }
            if (execution.truncated()) {
                throw new SandboxGateway.Failure(
                        "sandbox-file-output-truncated", "E2B 工作区归档输出被截断。", null);
            }
            return new ByteArrayInputStream(
                    Base64.getDecoder().decode(execution.stdout().trim()));
        }

        private void requireTarPersistence() {
            var mode = properties.getE2b().getPersistenceMode();
            if (mode != null && !"TAR".equalsIgnoreCase(mode.trim())) {
                throw new SandboxGateway.Failure(
                        "sandbox-provider-not-configured",
                        "E2B 仅支持 TAR 工作区持久化。",
                        null);
            }
        }

        private void createSandbox() throws Exception {
            var body = new LinkedHashMap<String, Object>();
            body.put("templateID", templateId);
            body.put("timeout", properties.sandboxIdleTimeoutSeconds());
            var node = postJson(apiBaseUrl() + "/sandboxes", body);
            applySandbox(node);
            if (!StringUtils.hasText(sandboxId)) {
                throw new IllegalStateException("E2B 创建沙箱响应缺少 sandboxID。");
            }
        }

        private boolean connectSandbox(String id) throws Exception {
            var body = new LinkedHashMap<String, Object>();
            body.put("timeout", properties.sandboxIdleTimeoutSeconds());
            var request = new Request.Builder()
                    .url(apiBaseUrl() + "/sandboxes/" + id + "/connect")
                    .addHeader("X-API-Key", requireApiKey())
                    .post(RequestBody.create(JsonUtils.objectToJson(body), JSON))
                    .build();
            return SandboxCalls.withRetries(properties.normalizedMaxRetries(), () -> {
                try (Response response = http.newCall(request).execute()) {
                    var text = response.body() == null ? "" : response.body().string();
                    if (response.code() == 404) {
                        return false;
                    }
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException(
                                "E2B 连接沙箱失败：HTTP " + response.code() + " " + text);
                    }
                    if (StringUtils.hasText(text)) {
                        applySandbox(json.readTree(text));
                    }
                    return StringUtils.hasText(sandboxId);
                }
            });
        }

        private void applySandbox(JsonNode node) {
            if (node == null) {
                return;
            }
            if (node.hasNonNull("sandboxID")) {
                sandboxId = node.get("sandboxID").asText();
            }
            if (node.hasNonNull("domain")) {
                domain = node.get("domain").asText();
            }
            if (node.hasNonNull("envdAccessToken")) {
                envdAccessToken = node.get("envdAccessToken").asText();
            }
        }

        private SandboxGateway.Execution runShell(String cwd, String command, int timeoutSeconds)
                throws Exception {
            var bounded = timeoutSeconds > 0;
            var callClientBuilder = http.newBuilder().readTimeout(0, TimeUnit.SECONDS);
            if (bounded) {
                callClientBuilder.callTimeout(
                        timeoutSeconds * 1000L + CLIENT_TIMEOUT_SLACK_MILLIS,
                        TimeUnit.MILLISECONDS);
            } else {
                callClientBuilder.callTimeout(0, TimeUnit.MILLISECONDS);
            }
            var body = startRequest(command, cwd);
            var url = envdUrl() + "/process.Process/Start";
            var requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(envelope(body), CONNECT_JSON))
                    .addHeader("Connect-Protocol-Version", "1")
                    .addHeader("User-Agent", "pudans-agent-e2b")
                    .addHeader("E2b-Sandbox-Id", sandboxId)
                    .addHeader("E2b-Sandbox-Port", Integer.toString(ENVD_PORT))
                    .addHeader("Authorization", basicAuthUser(properties.getE2b().getRunUser()));
            if (StringUtils.hasText(envdAccessToken)) {
                requestBuilder.addHeader("X-Access-Token", envdAccessToken);
            }
            if (bounded) {
                requestBuilder.addHeader("Connect-Timeout-Ms", Long.toString(timeoutSeconds * 1000L));
            }
            var startedAt = System.nanoTime();
            try (Response response = callClientBuilder.build().newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    var text = response.body() == null ? "" : response.body().string();
                    throw new IllegalStateException(
                            "envd Start failed HTTP " + response.code() + ": " + text);
                }
                var stdout = new ByteArrayOutputStream();
                var stderr = new ByteArrayOutputStream();
                try (InputStream in = response.body() == null
                        ? InputStream.nullInputStream()
                        : response.body().byteStream()) {
                    var exit = drain(in, stdout, stderr);
                    var elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt);
                    if (exit == null) {
                        if (bounded && elapsed >= timeoutSeconds) {
                            throw new ExecTimeoutException("沙箱命令执行超时。");
                        }
                        throw new IllegalStateException("envd 进程流在退出码前结束。");
                    }
                    var output = stdout.toByteArray();
                    var errors = stderr.toByteArray();
                    var truncated = output.length >= OUTPUT_TRUNCATE_BYTES
                            || errors.length >= OUTPUT_TRUNCATE_BYTES;
                    return new SandboxGateway.Execution(
                            exit,
                            truncate(output),
                            truncate(errors),
                            truncated);
                }
            } catch (InterruptedIOException error) {
                if (Thread.currentThread().isInterrupted() || !bounded) {
                    throw error;
                }
                throw new ExecTimeoutException("沙箱命令执行超时。", error);
            } catch (ConnectStreamException error) {
                if (bounded && CONNECT_DEADLINE_EXCEEDED.equals(error.code())) {
                    throw new ExecTimeoutException("沙箱命令执行超时。", error);
                }
                throw error;
            }
        }

        private Integer drain(InputStream in, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr)
                throws IOException {
            Integer exit = null;
            while (true) {
                var flags = in.read();
                if (flags == -1) {
                    break;
                }
                var lengthBytes = in.readNBytes(4);
                if (lengthBytes.length < 4) {
                    break;
                }
                var length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();
                if (length < 0 || length > 64 * 1024 * 1024) {
                    throw new IOException("非法的 Connect 帧长度：" + length);
                }
                var data = in.readNBytes(length);
                if (data.length < length) {
                    break;
                }
                if ((flags & 0x01) != 0) {
                    throw new IOException("不支持压缩的 Connect 帧。");
                }
                if ((flags & 0x02) != 0) {
                    var end = json.readTree(data);
                    var error = end.path("error");
                    if (error.isObject()) {
                        throw new ConnectStreamException(
                                error.path("code").asText("unknown"),
                                error.path("message").asText(""));
                    }
                    break;
                }
                if ((flags & 0xFC) != 0) {
                    continue;
                }
                JsonNode event;
                try {
                    event = json.readTree(data).path("event");
                } catch (IOException error) {
                    continue;
                }
                var dataNode = event.path("data");
                if (dataNode.isObject()) {
                    append(dataNode.path("stdout"), stdout);
                    append(dataNode.path("stderr"), stderr);
                }
                var endNode = event.path("end");
                if (endNode.isObject()) {
                    var exitNode = endNode.has("exitCode")
                            ? endNode.path("exitCode")
                            : endNode.path("exit_code");
                    if (exitNode.canConvertToInt()) {
                        exit = exitNode.intValue();
                    } else if (endNode.has("error")) {
                        exit = 1;
                    }
                    var error = endNode.path("error");
                    if (error.isTextual()) {
                        var bytes = error.textValue().getBytes(StandardCharsets.UTF_8);
                        if (stderr.size() > 0 && stderr.toByteArray()[stderr.size() - 1] != '\n') {
                            stderr.write('\n');
                        }
                        stderr.writeBytes(bytes);
                    }
                }
            }
            return exit;
        }

        private void append(JsonNode node, ByteArrayOutputStream output) {
            if (!node.isTextual()) {
                return;
            }
            try {
                output.writeBytes(Base64.getDecoder().decode(node.textValue()));
            } catch (IllegalArgumentException ignored) {
                // 忽略供应商返回的非法 base64 分块。
            }
        }

        private String envdUrl() {
            var effectiveDomain = StringUtils.hasText(domain)
                    ? domain
                    : properties.getE2b().getDomain().trim();
            return "https://" + ENVD_PORT + "-" + sandboxId + "." + effectiveDomain;
        }

        private String apiBaseUrl() {
            var base = properties.getE2b().getApiBaseUrl();
            var trimmed = StringUtils.hasText(base) ? base.trim() : "https://api.e2b.app";
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }

        private JsonNode postJson(String url, Map<String, Object> body) throws Exception {
            var request = new Request.Builder()
                    .url(url)
                    .addHeader("X-API-Key", requireApiKey())
                    .post(RequestBody.create(JsonUtils.objectToJson(body), JSON))
                    .build();
            try (Response response = http.newCall(request).execute()) {
                var text = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("E2B HTTP " + response.code() + "：" + text);
                }
                return StringUtils.hasText(text) ? json.readTree(text) : json.createObjectNode();
            }
        }

        private String basicAuthUser(String user) {
            var name = StringUtils.hasText(user) ? user.trim() : "user";
            return "Basic " + Base64.getEncoder()
                    .encodeToString((name + ":").getBytes(StandardCharsets.UTF_8));
        }

        private byte[] startRequest(String command, String cwd) {
            var process = new LinkedHashMap<String, Object>();
            process.put("cmd", "/bin/bash");
            process.put("args", List.of("-l", "-c", command));
            if (StringUtils.hasText(cwd)) {
                process.put("cwd", cwd);
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("process", Map.copyOf(process));
            body.put("stdin", false);
            return JsonUtils.objectToJson(body).getBytes(StandardCharsets.UTF_8);
        }

        private byte[] envelope(byte[] payload) {
            var out = new byte[5 + payload.length];
            out[0] = 0x00;
            ByteBuffer.wrap(out, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(payload.length);
            System.arraycopy(payload, 0, out, 5, payload.length);
            return out;
        }

        private String requireApiKey() {
            var key = properties.getE2b().getApiKey();
            if (!StringUtils.hasText(key)) {
                throw new SandboxGateway.Failure(
                        "sandbox-provider-not-configured", "E2B API Key 尚未配置。", null);
            }
            return key.trim();
        }

        private String truncate(byte[] value) {
            return new String(value, 0, Math.min(value.length, OUTPUT_TRUNCATE_BYTES), StandardCharsets.UTF_8);
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private record State(
            String sandboxId,
            String workspaceRoot,
            String templateId,
            String domain,
            String envdAccessToken) {}

    private static final class ConnectStreamException extends IOException {

        private final String code;

        private ConnectStreamException(String code, String message) {
            super("envd 进程流错误 [" + code + "]：" + message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}