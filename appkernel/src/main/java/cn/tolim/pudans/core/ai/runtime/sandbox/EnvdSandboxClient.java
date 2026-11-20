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
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// E2B 兼容 envd 数据面：命令、文件和日志共用 Connect 协议实现
final class EnvdSandboxClient {

    private static final int ENTRYPOINT_TIMEOUT_SECONDS = 30;

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final String provider;

    private final int requestTimeoutSeconds;

    EnvdSandboxClient(
            WebClient webClient,
            ObjectMapper mapper,
            String provider,
            int requestTimeoutSeconds) {
        this.webClient = webClient;
        this.mapper = mapper;
        this.provider = provider;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    Mono<SandboxExecResult> exec(String endpoint, String token, SandboxExecSpec spec) {
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
                SandboxSupport.writeJson(mapper, message, provider, "执行命令").getBytes(StandardCharsets.UTF_8));
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
                                    provider, "执行命令", error.code(), error.message()));
                        }))
                .timeout(Duration.ofSeconds((long) spec.timeoutSeconds() + requestTimeoutSeconds))
                .map(body -> processResult(body, started));
    }

    Mono<SandboxFile> readFile(String endpoint, String token, String sandboxId, String path) {
        return webClient
                .get()
                .uri(fileUri(endpoint, path))
                .accept(MediaType.ALL)
                .header("X-Access-Token", token)
                .exchangeToMono(response -> response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> response.statusCode().isError()
                                ? Mono.error(SandboxErrors.providerRejected(
                                        provider, "读取文件", "HTTP_" + response.statusCode().value(), null))
                                : Mono.just(body)))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .map(content -> SandboxFiles.fromBytes(path, content))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    Mono<Void> writeFile(String endpoint, String token, String sandboxId, String path, byte[] content) {
        var resource = new ByteArrayResource(content == null ? new byte[0] : content) {
            @Override
            public String getFilename() {
                return fileName(path);
            }
        };
        var multipart = new org.springframework.http.client.MultipartBodyBuilder();
        multipart.part("file", resource).contentType(MediaType.APPLICATION_OCTET_STREAM);
        return webClient
                .post()
                .uri(fileUri(endpoint, path))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-Access-Token", token)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(SandboxErrors.providerRejected(
                                        provider, "写入文件", "HTTP_" + response.statusCode().value(), null)));
                    }
                    return response.bodyToMono(Void.class);
                })
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .onErrorMap(error -> SandboxErrors.fileFailed(sandboxId, error));
    }

    Flux<SandboxLogEvent> logs(
            String endpoint, String token, String logFile, @Nullable String cursor) {
        var offset = parseCursor(cursor);
        var command = "tail -c +" + (offset + 1) + " " + SandboxSupport.quote(logFile) + " 2>/dev/null || true";
        var spec = new SandboxExecSpec(command, List.of(), null, Map.of(), ENTRYPOINT_TIMEOUT_SECONDS);
        return exec(endpoint, token, spec)
                .flatMapMany(result -> Flux.fromIterable(logEvents(result.stdout())));
    }

    Mono<Void> startEntrypoint(
            String endpoint, String token, SandboxCreateSpec spec, String logFile) {
        var entrypoint = spec.entrypoint();
        if (!StringUtils.hasText(entrypoint)) {
            return Mono.empty();
        }
        return exec(
                        endpoint,
                        token,
                        new SandboxExecSpec(
                                "nohup " + entrypoint + " >> " + SandboxSupport.quote(logFile) + " 2>&1 &",
                                List.of(),
                                spec.workdir(),
                                Map.of(),
                                ENTRYPOINT_TIMEOUT_SECONDS))
                .flatMap(result -> result.exitCode() == 0
                        ? Mono.empty()
                        : Mono.error(SandboxErrors.createFailed(
                                spec.agentName(), new IllegalStateException("entrypoint 启动失败"))));
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
                        throw SandboxErrors.providerRejected(provider, "执行命令", end.code(), end.message());
                    }
                    continue;
                }
                var node = SandboxSupport.readJson(
                        mapper,
                        new String(envelope.payload(), StandardCharsets.UTF_8),
                        provider,
                        "执行命令");
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
            throw SandboxErrors.providerResponse(provider, "执行命令", errorEnvelope);
        }
        if (exitCode == null) {
            throw SandboxErrors.providerResponse(
                    provider, "执行命令", new IllegalStateException("响应缺少结束事件"));
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

    private URI fileUri(String endpoint, String path) {
        return UriComponentsBuilder.fromUriString(endpoint)
                .path("/files")
                .queryParam("path", path)
                .build()
                .encode()
                .toUri();
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

    private @Nullable ConnectError errorPayload(byte[] payload) {
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

    private static void writeBytes(ByteArrayOutputStream target, @Nullable JsonNode encoded) {
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

    private static List<SandboxLogEvent> logEvents(@Nullable String content) {
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

    private static String fileName(String path) {
        try {
            var name = Path.of(path).getFileName();
            return name == null ? "file" : name.toString();
        } catch (RuntimeException error) {
            return "file";
        }
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

    private record ConnectError(String code, String message) {}
}