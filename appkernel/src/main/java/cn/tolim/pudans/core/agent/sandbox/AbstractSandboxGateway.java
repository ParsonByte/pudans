package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.infra.utils.Digests;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
abstract class AbstractSandboxGateway implements SandboxGateway {

    private static final int FILE_CHUNK_BYTES = 128 * 1024;

    private static final int FILE_TIMEOUT_SECONDS = 120;

    protected final SandboxProperties properties;

    private final SandboxClient client;

    private final Map<UUID, SandboxSession> active = new ConcurrentHashMap<>();

    protected AbstractSandboxGateway(SandboxProperties properties, SandboxClient client) {
        this.properties = properties;
        this.client = client;
    }

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
                .onErrorResume(SandboxSession.ExecTimeoutException.class, error -> Mono.error(
                        new ExecTimeout("沙箱命令执行超时。", error)))
                .onErrorResume(this::failure);
    }

    // 导出工作区归档流，供显式沙箱快照路由持久化。
    @Override
    public Mono<InputStream> persistWorkspace(UUID sandboxId, String providerState) {
        return Mono.fromCallable(() -> {
                    var session = activeSession(sandboxId, providerState);
                    if (!session.isRunning()) {
                        session.start();
                    }
                    return session.persistWorkspace();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure);
    }

    // 恢复已停止的供应商沙箱，并使用状态中的模板和地域重建会话。
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
                    var session = activeSession(sandboxId, providerState);
                    session.stop();
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure)
                .then();
    }

    // 显式销毁供应商沙箱并移除本地活动句柄。
    @Override
    public Mono<Void> destroy(UUID sandboxId, String providerState) {
        return Mono.fromCallable(() -> {
                    var session = active.get(sandboxId);
                    if (session == null) {
                        session = resumed(providerState);
                    }
                    session.shutdown();
                    active.remove(sandboxId);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(this::failure)
                .then();
    }

    private Created createBlocking(CreateCommand command) throws Exception {
        var session = client.create(
                configuredWorkspaceRoot(), command.template(), command.region());
        try {
            session.start();
            active.put(command.sandboxId(), session);
            return new Created(
                    session.providerSandboxId(), session.providerState(), session.workspaceRoot());
        } catch (Exception error) {
            shutdownQuietly(session);
            throw error;
        }
    }

    private Execution execBlocking(ExecCommand command) throws Exception {
        var session = activeSession(command.sandboxId(), command.providerState());
        if (!session.isRunning()) {
            session.start();
        }
        return run(session, command.command(), command.workdir(), command.timeoutSeconds());
    }

    private Resumed resumeBlocking(ResumeCommand command) throws Exception {
        if (!StringUtils.hasText(command.providerState())) {
            throw new Failure(
                    "sandbox-state-missing",
                    "沙箱缺少可恢复的供应商状态。",
                    null);
        }
        var session = activeSession(command.sandboxId(), command.providerState());
        if (!session.isRunning()) {
            session.start();
        }
        return new Resumed(
                session.providerSandboxId(), session.providerState(), session.workspaceRoot());
    }

    private void uploadBlocking(UploadCommand command) throws Exception {
        if (command.content() == null || command.content().length == 0) {
            throw new Failure("sandbox-file-empty", "上传文件不能为空。", null);
        }
        if (command.content().length > properties.normalizedMaxFileBytes()) {
            throw new Failure("sandbox-file-too-large", "上传文件大小超过限制。", null);
        }
        var session = activeSession(command.sandboxId(), command.providerState());
        var temp = "/tmp/pudans-upload-" + command.sandboxId() + ".b64";
        var directory = parent(command.path());
        var encoded = Base64.getEncoder().encodeToString(command.content());
        try {
            requireSuccess(
                    run(session, "mkdir -p "
                            + shellQuote(directory)
                            + " && rm -f "
                            + shellQuote(command.path())
                            + " "
                            + shellQuote(temp), null, 30),
                    "sandbox-file-write-failed");
            for (var offset = 0; offset < encoded.length(); offset += 4_000) {
                var chunk = encoded.substring(offset, Math.min(encoded.length(), offset + 4_000));
                requireSuccess(
                        run(session, "printf '%s' "
                                + shellQuote(chunk)
                                + " >> "
                                + shellQuote(temp), null, FILE_TIMEOUT_SECONDS),
                        "sandbox-file-write-failed");
            }
            requireSuccess(
                    run(session, "base64 -d "
                            + shellQuote(temp)
                            + " > "
                            + shellQuote(command.path())
                            + " && rm -f "
                            + shellQuote(temp), null, FILE_TIMEOUT_SECONDS),
                    "sandbox-file-write-failed");
            var actual = run(
                            session,
                            "sha256sum " + shellQuote(command.path()) + " | cut -d' ' -f1",
                            null,
                            FILE_TIMEOUT_SECONDS)
                    .stdout();
            if (actual == null || !command.sha256().equalsIgnoreCase(actual.trim())) {
                run(session, "rm -f " + shellQuote(command.path()), null, 30);
                throw new Failure("sandbox-file-integrity", "上传文件摘要校验失败。", null);
            }
        } catch (Exception error) {
            runQuietly(session, "rm -f " + shellQuote(temp));
            throw error;
        }
    }

    private Downloaded downloadBlocking(DownloadCommand command) throws Exception {
        var session = activeSession(command.sandboxId(), command.providerState());
        var size = run(
                        session,
                        "test -f " + shellQuote(command.path())
                                + " && wc -c < " + shellQuote(command.path()),
                        null,
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
                    session,
                    "dd if=" + shellQuote(command.path())
                            + " bs=" + FILE_CHUNK_BYTES
                            + " skip=" + index
                            + " count=1 2>/dev/null | base64 | tr -d '\\n'",
                    null,
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

    private SandboxSession activeSession(UUID sandboxId, String providerState) {
        var existing = active.get(sandboxId);
        if (existing != null) {
            return existing;
        }
        var resumed = resumed(providerState);
        var previous = active.putIfAbsent(sandboxId, resumed);
        return previous == null ? resumed : previous;
    }

    private SandboxSession resumed(String providerState) {
        return client.resume(providerState);
    }

    private Execution run(
            SandboxSession session, String command, String workdir, int timeoutSeconds)
            throws Exception {
        return session.exec(null, command(workdir, command), timeoutSeconds);
    }

    private void runQuietly(SandboxSession session, String command) {
        try {
            run(session, command, null, 30);
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

    private <T> Mono<T> failure(Throwable error) {
        if (error instanceof Failure || error instanceof ExecTimeout) {
            return Mono.error(error);
        }
        log.warn("{} 沙箱供应商调用失败：{}", providerLabel(), error.getMessage());
        return Mono.error(new Failure(
                "sandbox-provider-failed",
                providerLabel() + " 沙箱供应商调用失败。",
                error));
    }

    private void shutdownQuietly(SandboxSession session) {
        try {
            session.shutdown();
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
}