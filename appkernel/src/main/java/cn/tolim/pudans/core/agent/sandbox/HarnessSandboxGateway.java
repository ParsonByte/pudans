package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.infra.utils.Digests;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
abstract class HarnessSandboxGateway<O extends SandboxClientOptions> implements SandboxGateway {

    private static final int FILE_CHUNK_BYTES = 128 * 1024;

    private static final int FILE_TIMEOUT_SECONDS = 120;

    protected final SandboxProperties properties;

    private final Function<O, SandboxClient<O>> clients;

    private final Map<UUID, ActiveSandbox<O>> active = new ConcurrentHashMap<>();

    protected HarnessSandboxGateway(
            SandboxProperties properties, Function<O, SandboxClient<O>> clients) {
        this.properties = properties;
        this.clients = clients;
    }

    protected abstract O options(String template, String region);

    protected abstract String providerSandboxId(SandboxState state);

    protected abstract String configuredWorkspaceRoot();

    protected abstract String providerLabel();

    // 创建真实供应商沙箱并保存可恢复的供应商状态。
    @Override
    public Mono<Created> create(CreateCommand command) {
        if (!configured()) {
            return Mono.error(new Failure(
                    "sandbox-provider-not-configured",
                    providerLabel() + " 沙箱供应商尚未配置。",
                    null));
        }
        return Mono.fromCallable(() -> createBlocking(command))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure);
    }

    // 在供应商沙箱内执行命令，非零退出码作为真实结果返回。
    @Override
    public Mono<Execution> exec(ExecCommand command) {
        return Mono.fromCallable(() -> execBlocking(command))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(SandboxException.ExecTimeoutException.class, error -> Mono.error(
                        new ExecTimeout("沙箱命令执行超时。", error)))
                .onErrorResume(this::failure);
    }

    // 导出工作区归档流，供显式沙箱快照路由持久化。
    @Override
    public Mono<InputStream> persistWorkspace(UUID sandboxId, String providerState) {
        return Mono.fromCallable(() -> {
                    var sandbox = activeSandbox(sandboxId, providerState);
                    if (!sandbox.sandbox().isRunning()) {
                        sandbox.sandbox().start();
                    }
                    return sandbox.sandbox().persistWorkspace();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure);
    }

    // 恢复已停止的供应商沙箱，并使用沙箱记录中的模板和地域重建客户端。
    @Override
    public Mono<Resumed> resume(ResumeCommand command) {
        return Mono.fromCallable(() -> resumeBlocking(command))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure);
    }

    // 通过受控分块写入文件，并在远端重新计算摘要校验完整性。
    @Override
    public Mono<Void> upload(UploadCommand command) {
        return Mono.fromCallable(() -> {
                    uploadBlocking(command);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure)
                .then();
    }

    // 通过分块读取下载文件，避免触发供应商单次输出截断。
    @Override
    public Mono<Downloaded> download(DownloadCommand command) {
        return Mono.fromCallable(() -> downloadBlocking(command))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure);
    }

    // 停止沙箱但保留供应商侧资源，便于后续恢复或显式销毁。
    @Override
    public Mono<Void> stop(UUID sandboxId, String providerState) {
        return Mono.fromCallable(() -> {
                    var sandbox = activeSandbox(sandboxId, providerState);
                    sandbox.sandbox().stop();
                    return sandbox;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure)
                .then();
    }

    // 显式销毁供应商沙箱并移除本地活动句柄。
    @Override
    public Mono<Void> destroy(UUID sandboxId, String providerState) {
        return Mono.fromCallable(() -> {
                    var sandbox = active.get(sandboxId);
                    if (sandbox == null) {
                        sandbox = resumed(sandboxId, providerState);
                    }
                    sandbox.sandbox().shutdown();
                    active.remove(sandboxId);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure)
                .then();
    }

    private Created createBlocking(CreateCommand command) throws Exception {
        var options = options(command.template(), command.region());
        var client = clients.apply(options);
        var workspace = new WorkspaceSpec();
        workspace.setRoot(configuredWorkspaceRoot());
        workspace.setEnvironment(Map.of());
        var sandbox = client.create(workspace, new NoopSnapshotSpec(), options);
        try {
            sandbox.start();
            var state = sandbox.getState();
            var providerState = client.serializeState(state);
            active.put(
                    command.sandboxId(),
                    new ActiveSandbox<>(client, sandbox, providerState));
            return new Created(
                    providerSandboxId(state), providerState, workspaceRoot(state));
        } catch (Exception error) {
            shutdownQuietly(sandbox);
            throw error;
        }
    }

    private Execution execBlocking(ExecCommand command) throws Exception {
        var sandbox = activeSandbox(command.sandboxId(), command.providerState());
        if (!sandbox.sandbox().isRunning()) {
            sandbox.sandbox().start();
        }
        return run(
                sandbox.sandbox(),
                command(command.workdir(), command.command()),
                command.timeoutSeconds());
    }

    private Resumed resumeBlocking(ResumeCommand command) throws Exception {
        if (!StringUtils.hasText(command.providerState())) {
            throw new Failure(
                    "sandbox-state-missing",
                    "沙箱缺少可恢复的供应商状态。",
                    null);
        }
        var sandbox = active.get(command.sandboxId());
        if (sandbox == null) {
            var resumed = resumed(
                    command.sandboxId(),
                    command.providerState(),
                    command.template(),
                    command.region());
            var previous = active.putIfAbsent(command.sandboxId(), resumed);
            sandbox = previous == null ? resumed : previous;
        }
        if (!sandbox.sandbox().isRunning()) {
            sandbox.sandbox().start();
        }
        var state = sandbox.sandbox().getState();
        var providerState = sandbox.client().serializeState(state);
        active.put(
                command.sandboxId(),
                new ActiveSandbox<>(sandbox.client(), sandbox.sandbox(), providerState));
        return new Resumed(
                providerSandboxId(state), providerState, workspaceRoot(state));
    }

    private void uploadBlocking(UploadCommand command) throws Exception {
        if (command.content() == null || command.content().length == 0) {
            throw new Failure("sandbox-file-empty", "上传文件不能为空。", null);
        }
        if (command.content().length > properties.normalizedMaxFileBytes()) {
            throw new Failure("sandbox-file-too-large", "上传文件大小超过限制。", null);
        }
        var sandbox = activeSandbox(
                command.sandboxId(),
                command.providerState(),
                command.template(),
                command.region());
        var temp = "/tmp/pudans-upload-" + command.sandboxId() + ".b64";
        var directory = parent(command.path());
        var encoded = Base64.getEncoder().encodeToString(command.content());
        try {
            requireSuccess(
                    run(sandbox.sandbox(), "mkdir -p "
                            + shellQuote(directory)
                            + " && rm -f "
                            + shellQuote(command.path())
                            + " "
                            + shellQuote(temp), 30),
                    "sandbox-file-write-failed");
            for (var offset = 0; offset < encoded.length(); offset += 4_000) {
                var chunk = encoded.substring(offset, Math.min(encoded.length(), offset + 4_000));
                requireSuccess(
                        run(sandbox.sandbox(), "printf '%s' "
                                + shellQuote(chunk)
                                + " >> "
                                + shellQuote(temp), FILE_TIMEOUT_SECONDS),
                        "sandbox-file-write-failed");
            }
            requireSuccess(
                    run(sandbox.sandbox(), "base64 -d "
                            + shellQuote(temp)
                            + " > "
                            + shellQuote(command.path())
                            + " && rm -f "
                            + shellQuote(temp), FILE_TIMEOUT_SECONDS),
                    "sandbox-file-write-failed");
            var actual = run(
                            sandbox.sandbox(),
                            "sha256sum " + shellQuote(command.path()) + " | cut -d' ' -f1",
                            FILE_TIMEOUT_SECONDS)
                    .stdout();
            if (actual == null || !command.sha256().equalsIgnoreCase(actual.trim())) {
                run(sandbox.sandbox(), "rm -f " + shellQuote(command.path()), 30);
                throw new Failure("sandbox-file-integrity", "上传文件摘要校验失败。", null);
            }
        } catch (Exception error) {
            runQuietly(sandbox.sandbox(), "rm -f " + shellQuote(temp));
            throw error;
        }
    }

    private Downloaded downloadBlocking(DownloadCommand command) throws Exception {
        var sandbox = activeSandbox(
                command.sandboxId(),
                command.providerState(),
                command.template(),
                command.region());
        var size = run(
                        sandbox.sandbox(),
                        "test -f " + shellQuote(command.path())
                                + " && wc -c < " + shellQuote(command.path()),
                        30)
                .stdout();
        if (size == null || !StringUtils.hasText(size)) {
            throw new Failure("sandbox-file-not-found", "沙箱文件不存在。", null);
        }
        long total;
        try {
            total = Long.parseLong(size.trim());
        } catch (NumberFormatException error) {
            throw new Failure("sandbox-file-read-failed", "无法读取沙箱文件大小。", error);
        }
        if (total > properties.normalizedMaxFileBytes()) {
            throw new Failure("sandbox-file-too-large", "沙箱文件大小超过限制。", null);
        }
        var output = new ByteArrayOutputStream((int) Math.min(total, FILE_CHUNK_BYTES));
        var chunks = (int) ((total + FILE_CHUNK_BYTES - 1) / FILE_CHUNK_BYTES);
        for (var index = 0; index < chunks; index++) {
            var execution = run(
                    sandbox.sandbox(),
                    "dd if=" + shellQuote(command.path())
                            + " bs=" + FILE_CHUNK_BYTES
                            + " skip=" + index
                            + " count=1 2>/dev/null | base64 | tr -d '\\n'",
                    FILE_TIMEOUT_SECONDS);
            requireSuccess(execution, "sandbox-file-read-failed");
            if (execution.truncated()) {
                throw new Failure("sandbox-file-output-truncated", "沙箱文件分块输出被截断。", null);
            }
            output.write(Base64.getDecoder().decode(execution.stdout().trim()));
        }
        var content = output.toByteArray();
        if (content.length != total) {
            throw new Failure("sandbox-file-integrity", "下载文件大小校验失败。", null);
        }
        return new Downloaded(content, Digests.sha256Hex(content));
    }

    private ActiveSandbox<O> activeSandbox(UUID sandboxId, String providerState) throws Exception {
        return activeSandbox(sandboxId, providerState, null, null);
    }

    private ActiveSandbox<O> activeSandbox(
            UUID sandboxId, String providerState, String template, String region) throws Exception {
        var existing = active.get(sandboxId);
        if (existing != null) {
            return existing;
        }
        var resumed = resumed(sandboxId, providerState, template, region);
        var previous = active.putIfAbsent(sandboxId, resumed);
        return previous == null ? resumed : previous;
    }

    private ActiveSandbox<O> resumed(UUID sandboxId, String providerState) {
        return resumed(sandboxId, providerState, null, null);
    }

    private ActiveSandbox<O> resumed(
            UUID sandboxId, String providerState, String template, String region) {
        var options = options(template, region);
        var client = clients.apply(options);
        var state = client.deserializeState(providerState);
        var sandbox = client.resume(state);
        return new ActiveSandbox<>(client, sandbox, providerState);
    }

    private Execution run(Sandbox sandbox, String command, int timeoutSeconds) throws Exception {
        try {
            var result = sandbox.exec(null, command, timeoutSeconds);
            return new Execution(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.truncated());
        } catch (SandboxException.ExecException error) {
            return new Execution(
                    error.getExitCode(),
                    error.getStdout(),
                    error.getStderr(),
                    false);
        }
    }

    private void runQuietly(Sandbox sandbox, String command) {
        try {
            run(sandbox, command, 30);
        } catch (Exception error) {
            log.warn("清理 {} 沙箱临时文件失败：{}", providerLabel(), error.getMessage());
        }
    }

    private static void requireSuccess(Execution execution, String code) {
        if (execution.exitCode() != 0) {
            throw new Failure(code, "沙箱文件操作失败。", null);
        }
    }

    private static String parent(String path) {
        var separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    protected String workspaceRoot(SandboxState state) {
        var workspace = state == null ? null : state.getWorkspaceSpec();
        return workspace != null && StringUtils.hasText(workspace.getRoot())
                ? workspace.getRoot()
                : configuredWorkspaceRoot();
    }

    private <T> Mono<T> failure(Throwable error) {
        if (error instanceof Failure || error instanceof ExecTimeout) {
            return Mono.error(error);
        }
        if (error instanceof SandboxException.SandboxConfigurationException) {
            return Mono.error(new Failure(
                    "sandbox-provider-not-configured",
                    providerLabel() + " 沙箱供应商配置无效。",
                    error));
        }
        log.warn("{} 沙箱供应商调用失败：{}", providerLabel(), error.getMessage());
        return Mono.error(new Failure(
                "sandbox-provider-failed",
                providerLabel() + " 沙箱供应商调用失败。",
                error));
    }

    private void shutdownQuietly(Sandbox sandbox) {
        try {
            sandbox.shutdown();
        } catch (Exception error) {
            log.warn("清理未完成创建的 {} 沙箱失败：{}", providerLabel(), error.getMessage());
        }
    }

    // 工作目录参数已经在边界校验，这里只负责安全引用。
    private static String command(String workdir, String command) {
        if (!StringUtils.hasText(workdir)) {
            return command;
        }
        return "cd " + shellQuote(workdir) + " && (" + command + ")";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private record ActiveSandbox<O extends SandboxClientOptions>(
            SandboxClient<O> client,
            Sandbox sandbox,
            String providerState) {}
}
