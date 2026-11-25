package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.contract.agent.dto.SandboxCreateRequest;
import cn.tolim.pudans.contract.agent.dto.SandboxExecRequest;
import cn.tolim.pudans.contract.agent.dto.SandboxExecResult;
import cn.tolim.pudans.contract.agent.dto.SandboxFileResult;
import cn.tolim.pudans.contract.agent.dto.SandboxOperationPage;
import cn.tolim.pudans.contract.agent.dto.SandboxPage;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.sandbox.SandboxStore;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class Sandboxes {

    private static final String OPERATION_EXEC = "exec";

    private static final String OPERATION_UPLOAD = "upload";

    private static final String OPERATION_DOWNLOAD = "download";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private static final int MIN_TTL_SECONDS = 60;

    private final SandboxStore store;

    private final AgentSessionRepository sessions;

    private final AgentRunRepository runs;

    private final SandboxGateways gateways;

    private final SandboxLeases leases;

    private final SandboxExecGuard guard;

    private final SandboxEvents events;

    private final SandboxSnapshots snapshots;

    private final SandboxProperties properties;

    private Clock clock = Clock.systemUTC();

    public record Creation(Sandbox sandbox, boolean created) {}

    public record DownloadResult(byte[] content, String sha256, String fileName) {}

    // 使用归属与幂等键生成稳定沙箱标识，重复请求返回同一真实资源。
    public Mono<Creation> create(
            RequestIdentity identity, SandboxCreateRequest request, String idempotencyKey) {
        var idempotencyError = validateIdempotency(idempotencyKey);
        if (idempotencyError != null) {
            return Mono.error(idempotencyError);
        }
        if (request == null) {
            return Mono.error(requestRequired());
        }
        var id = idempotentId(identity, idempotencyKey);
        return store.findById(id)
                .flatMap(existing -> replay(identity, request, existing))
                .switchIfEmpty(Mono.defer(() -> prepare(identity, request, id)
                        .flatMap(session -> insertAndProvision(identity, request, session))));
    }

    // 按创建时间与标识游标列出当前归属的沙箱。
    public Mono<SandboxPage> list(
            RequestIdentity identity, CreationCursor.Position cursor, int requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        return store
                .listMine(identity.ownerId(), identity.workspaceId(), cursor, limit)
                .map(SandboxStore.SessionRow::toContract)
                .collectList()
                .map(items -> new SandboxPage(
                        items, nextCursor(items, limit), limit));
    }

    public Mono<Sandbox> view(RequestIdentity identity, UUID sandboxId) {
        return requireOwned(identity, sandboxId).map(SandboxStore.SessionRow::toContract);
    }

    // 按沙箱内启动时间与操作标识游标列出操作记录。
    public Mono<SandboxOperationPage> operations(
            RequestIdentity identity,
            UUID sandboxId,
            CreationCursor.Position cursor,
            int requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        return requireOwned(identity, sandboxId)
                .flatMapMany(row -> store.listOperations(row.id(), cursor, limit))
                .collectList()
                .map(items -> new SandboxOperationPage(
                        items, nextOperationCursor(items, limit), limit));
    }

    public Mono<SandboxOperation> operation(
            RequestIdentity identity, UUID sandboxId, UUID operationId) {
        return requireOwned(identity, sandboxId)
                .then(store.findOperation(sandboxId, operationId))
                .switchIfEmpty(Mono.error(operationNotFound()));
    }

    // 每次命令生成操作记录，成功、失败和超时都落审计事实。
    public Mono<SandboxExecResult> exec(
            RequestIdentity identity, UUID sandboxId, SandboxExecRequest request) {
        return requireOwned(identity, sandboxId).flatMap(row -> {
            var sandbox = row.toContract();
            if (!sandbox.status().acceptsCommand()) {
                return Mono.error(notReady());
            }
            return gateway(sandbox.provider())
                    .flatMap(gateway -> {
                        var command = guard.validate(request, sandbox.workspaceRoot());
                        return withLease(
                                sandboxId,
                                performExec(sandbox, row.providerState(), gateway, command));
                    });
        });
    }

    // 快照先通过显式路由校验，再串行执行工作区导出和对象存储归档。
    public Mono<SandboxSnapshot> snapshot(RequestIdentity identity, UUID sandboxId) {
        return requireOwned(identity, sandboxId).flatMap(row -> {
            var sandbox = row.toContract();
            if (!sandbox.status().acceptsSnapshot()) {
                return Mono.error(notReady());
            }
            return gateway(sandbox.provider())
                    .flatMap(gateway -> snapshots.route()
                            .flatMap(policy -> withLease(
                                    sandboxId,
                                    performSnapshot(
                                            sandbox, row.providerState(), gateway, policy))));
        });
    }

    // 停止供应商运行时并保留可恢复状态。
    public Mono<Sandbox> stop(RequestIdentity identity, UUID sandboxId) {
        return requireOwned(identity, sandboxId).flatMap(sandbox -> {
            if (sandbox.status() == Sandbox.Status.STOPPED) {
                return Mono.just(sandbox.toContract());
            }
            if (sandbox.status().terminal()) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-terminal",
                        "沙箱已经结束，不能停止。"));
            }
            if (!sandbox.status().acceptsSnapshot()) {
                return Mono.error(notReady());
            }
            return gateway(sandbox.provider())
                    .flatMap(gateway -> withLease(
                            sandboxId, performStop(sandbox, gateway)));
        });
    }

    // 已就绪沙箱直接返回，只有已停止沙箱进入恢复状态机。
    public Mono<Sandbox> resume(RequestIdentity identity, UUID sandboxId) {
        return requireOwned(identity, sandboxId).flatMap(row -> {
            var sandbox = row.toContract();
            if (sandbox.status() == Sandbox.Status.READY
                    || sandbox.status() == Sandbox.Status.RUNNING) {
                return Mono.just(sandbox);
            }
            if (sandbox.status() != Sandbox.Status.STOPPED) {
                return Mono.error(resumeNotAllowed());
            }
            return gateway(sandbox.provider())
                    .flatMap(gateway -> withLease(
                            sandboxId, performResume(row, gateway)));
        });
    }

    // 文件写入先做路径和大小校验，再通过供应商受控分块写入。
    public Mono<SandboxFileResult> upload(
            RequestIdentity identity, UUID sandboxId, String requestedPath, byte[] content) {
        return requireOwned(identity, sandboxId).flatMap(row -> {
            var sandbox = row.toContract();
            if (!sandbox.status().acceptsCommand()) {
                return Mono.error(notReady());
            }
            var path = guard.validateFilePath(sandbox.workspaceRoot(), requestedPath);
            var size = guard.validateUploadSize(content);
            var sha256 = Digests.sha256Hex(content);
            return gateway(sandbox.provider())
                    .flatMap(gateway -> withLease(
                            sandboxId,
                            performUpload(row, gateway, path, content, size, sha256)));
        });
    }

    // 文件下载限制在工作区内，并通过分块输出避免供应商截断。
    public Mono<DownloadResult> download(
            RequestIdentity identity, UUID sandboxId, String requestedPath) {
        return requireOwned(identity, sandboxId).flatMap(row -> {
            var sandbox = row.toContract();
            if (!sandbox.status().acceptsCommand()) {
                return Mono.error(notReady());
            }
            var path = guard.validateFilePath(sandbox.workspaceRoot(), requestedPath);
            return gateway(sandbox.provider())
                    .flatMap(gateway -> withLease(
                            sandboxId, performDownload(row, gateway, path)));
        });
    }

    // 销毁供应商资源并保留终态记录。
    public Mono<Void> delete(RequestIdentity identity, UUID sandboxId) {
        return requireOwned(identity, sandboxId).flatMap(sandbox -> {
            if (sandbox.status() == Sandbox.Status.CANCELLED) {
                return Mono.empty();
            }
            return gateway(sandbox.provider())
                    .flatMap(gateway -> withLease(
                            sandboxId, performDelete(sandbox, gateway)));
        });
    }

    public Mono<SandboxStore.SessionRow> requireOwned(RequestIdentity identity, UUID sandboxId) {
        return store
                .findOwned(sandboxId, identity.ownerId(), identity.workspaceId())
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(this::expireIfNeeded);
    }

    private Mono<Creation> replay(
            RequestIdentity identity, SandboxCreateRequest request, SandboxStore.SessionRow existing) {
        if (!Objects.equals(existing.ownerId(), identity.ownerId())
                || !Objects.equals(existing.workspaceId(), identity.workspaceId())) {
            return Mono.error(idempotencyConflict());
        }
        if (!matches(existing, request)) {
            return Mono.error(idempotencyConflict());
        }
        return Mono.just(new Creation(existing.toContract(), false));
    }

    private Mono<SandboxStore.SessionRow> prepare(
            RequestIdentity identity, SandboxCreateRequest request, UUID id) {
        if (request.agentSessionId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-agent-session-required",
                    "Agent 会话标识不能为空。"));
        }
        if (!StringUtils.hasText(request.provider())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "sandbox-provider-required", "沙箱供应商不能为空。"));
        }
        var provider = request.provider().trim().toLowerCase();
        var gateway = gateways.find(provider).orElse(null);
        if (gateway == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-provider-unsupported",
                    "当前没有该沙箱供应商的真实实现。"));
        }
        if (!gateway.configured()) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "sandbox-provider-not-configured",
                    "沙箱供应商尚未配置。"));
        }
        var template = template(request, provider);
        if (!StringUtils.hasText(template)) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "sandbox-template-not-configured",
                    "沙箱模板尚未配置。"));
        }
        var region = region(request, provider);
        var ttl = ttlSeconds(request);
        if (ttl < MIN_TTL_SECONDS || ttl > properties.normalizedMaxTtlSeconds()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-ttl-invalid",
                    "沙箱有效期必须在 60 秒到配置上限之间。"));
        }
        return sessions
                .findByIdAndOwnerIdAndWorkspaceId(
                        request.agentSessionId(), identity.ownerId(), identity.workspaceId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "sandbox-agent-session-not-found", "Agent 会话不存在。")))
                .flatMap(session -> ensureRun(request.runId(), session.getId()))
                .thenReturn(row(
                        id,
                        identity,
                        request.agentSessionId(),
                        request.runId(),
                        template,
                        region,
                        provider,
                        ttl));
    }

    private Mono<Void> ensureRun(UUID runId, UUID sessionId) {
        if (runId == null) {
            return Mono.empty();
        }
        return runs.findByIdAndSessionId(runId, sessionId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "sandbox-agent-run-not-found", "Agent 运行不存在。")))
                .then();
    }

    private SandboxStore.SessionRow row(
            UUID id,
            RequestIdentity identity,
            UUID agentSessionId,
            UUID runId,
            String template,
            String region,
            String provider,
            long ttlSeconds) {
        var now = now();
        return new SandboxStore.SessionRow(
                id,
                identity.ownerId(),
                identity.workspaceId(),
                agentSessionId,
                runId,
                provider,
                template,
                region,
                Sandbox.Status.REQUESTED,
                properties.defaultWorkspaceRoot(provider),
                null,
                "{}",
                now.plusSeconds(ttlSeconds),
                null,
                now,
                null,
                null,
                null,
                null);
    }

    private Mono<Creation> insertAndProvision(
            RequestIdentity identity,
            SandboxCreateRequest request,
            SandboxStore.SessionRow session) {
        return store.insert(session).flatMap(insertion -> {
            if (!insertion.created()) {
                return replay(identity, request, insertion.session());
            }
            return gateway(session.provider())
                    .flatMap(gateway -> provision(insertion.session(), gateway));
        });
    }

    private Mono<Creation> provision(
            SandboxStore.SessionRow session, SandboxGateway gateway) {
        var provisioningAt = now();
        return store.markProvisioning(session.id(), provisioningAt)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-create-conflict",
                        "沙箱状态已变化，不能重复创建。")))
                .flatMap(current -> Mono.zip(
                        Mono.just(current),
                        gateway.create(new SandboxGateway.CreateCommand(
                                session.id(),
                                session.agentSessionId(),
                                session.template(),
                                session.region()))))
                .flatMap(tuple -> store.markReady(
                                session.id(),
                                tuple.getT2().providerSandboxId(),
                                tuple.getT2().providerState(),
                                tuple.getT2().workspaceRoot(),
                                now())
                        .switchIfEmpty(Mono.defer(() -> gateway
                                .destroy(session.id(), tuple.getT2().providerState())
                                .onErrorResume(ignored -> Mono.empty())
                                .then(Mono.error(new ApiException(
                                        HttpStatus.CONFLICT,
                                        "sandbox-create-conflict",
                                        "沙箱状态已变化，不能完成创建。"))))))
                .flatMap(ready -> events
                        .publish(ready.id(), SandboxEvents.READY, Map.of(
                                "status", ready.status(),
                                "expiresAt", ready.expiresAt(),
                                "workspaceRoot", ready.workspaceRoot()))
                        .thenReturn(new Creation(ready.toContract(), true)))
                .onErrorResume(error -> failCreate(session.id(), error));
    }

    private Mono<Creation> failCreate(UUID sandboxId, Throwable error) {
        var failure = failure(error, "sandbox-provision-failed");
        return store.markFailed(sandboxId, failure.getCode(), reason(failure), now())
                .flatMap(failed -> events
                        .publish(failed.id(), SandboxEvents.FAILED, Map.of(
                                "status", failed.status(),
                                "errorCode", failure.getCode(),
                                "errorMessage", reason(failure)))
                        .thenReturn(true))
                .then(Mono.error(failure));
    }

    private Mono<SandboxExecResult> performExec(
            Sandbox sandbox,
            String providerState,
            SandboxGateway gateway,
            SandboxExecGuard.Command command) {
        var startedAt = now();
        return store.markRunning(sandbox.id(), startedAt)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-state-conflict",
                        "沙箱状态已变化，不能执行命令。")))
                .flatMap(running -> store.insertOperation(
                        sandbox.id(), OPERATION_EXEC, command.command(), command.timeoutSeconds(), startedAt))
                .flatMap(operation -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("operationId", operation.id());
                    data.put("operationType", operation.operationType());
                    data.put("timeoutSeconds", operation.timeoutSeconds());
                    return events
                            .publish(sandbox.id(), SandboxEvents.OPERATION_STARTED, data)
                            .then(gateway.exec(new SandboxGateway.ExecCommand(
                                    sandbox.id(),
                                    providerState,
                                    command.command(),
                                    command.workdir(),
                                    command.timeoutSeconds())))
                            .flatMap(execution -> completeExec(sandbox, operation, execution))
                            .onErrorResume(
                                    SandboxGateway.ExecTimeout.class,
                                    error -> completeTimeout(sandbox, operation))
                            .onErrorResume(error -> failExec(sandbox, operation, error));
                });
    }

    private Mono<SandboxExecResult> completeExec(
            Sandbox sandbox, SandboxOperation operation, SandboxGateway.Execution execution) {
        var stdout = limit(execution.stdout());
        var stderr = limit(execution.stderr());
        var truncated = execution.truncated()
                || !stdout.equals(execution.stdout())
                || !stderr.equals(execution.stderr());
        var status = execution.exitCode() == 0
                ? SandboxOperation.Status.SUCCEEDED
                : SandboxOperation.Status.FAILED;
        var finishedAt = now();
        return store.finishOperation(
                        operation.id(),
                        status,
                        execution.exitCode(),
                        null,
                        null,
                        truncated,
                        null,
                        null,
                        finishedAt)
                .flatMap(finished -> {
                    var output = new LinkedHashMap<String, Object>();
                    output.put("operationId", operation.id());
                    output.put("stdout", stdout);
                    output.put("stderr", stderr);
                    output.put("truncated", truncated);
                    var completed = new LinkedHashMap<String, Object>();
                    completed.put("operationId", operation.id());
                    completed.put("status", status.getCode());
                    completed.put("exitCode", execution.exitCode());
                    completed.put("truncated", truncated);
                    return events
                            .publish(sandbox.id(), SandboxEvents.OPERATION_OUTPUT, output)
                            .then(events.publish(
                                    sandbox.id(), SandboxEvents.OPERATION_FINISHED, completed))
                            .thenReturn(new SandboxExecResult(
                                    operation.id(),
                                    status,
                                    execution.exitCode(),
                                    stdout,
                                    stderr,
                                    truncated,
                                    operation.startedAt(),
                                    finishedAt));
                });
    }

    private Mono<SandboxExecResult> completeTimeout(
            Sandbox sandbox, SandboxOperation operation) {
        var finishedAt = now();
        return store.finishOperation(
                        operation.id(),
                        SandboxOperation.Status.TIMED_OUT,
                        null,
                        null,
                        null,
                        false,
                        "sandbox-exec-timeout",
                        "命令执行超时。",
                        finishedAt)
                .flatMap(finished -> events
                        .publish(
                                sandbox.id(),
                                SandboxEvents.OPERATION_FINISHED,
                                Map.of(
                                        "operationId", operation.id(),
                                        "status", SandboxOperation.Status.TIMED_OUT.getCode(),
                                        "errorCode", "sandbox-exec-timeout"))
                        .thenReturn(new SandboxExecResult(
                                operation.id(),
                                SandboxOperation.Status.TIMED_OUT,
                                null,
                                "",
                                "",
                                false,
                                operation.startedAt(),
                                finishedAt)));
    }

    private Mono<SandboxExecResult> failExec(
            Sandbox sandbox, SandboxOperation operation, Throwable error) {
        var failure = failure(error, "sandbox-exec-failed");
        return store.finishOperation(
                        operation.id(),
                        SandboxOperation.Status.FAILED,
                        null,
                        null,
                        null,
                        false,
                        failure.getCode(),
                        reason(failure),
                        now())
                .flatMap(finished -> events
                        .publish(
                                sandbox.id(),
                                SandboxEvents.OPERATION_FINISHED,
                                Map.of(
                                        "operationId", operation.id(),
                                        "status", SandboxOperation.Status.FAILED.getCode(),
                                        "errorCode", failure.getCode()))
                        .thenReturn(true))
                .then(Mono.error(failure));
    }

    private Mono<SandboxSnapshot> performSnapshot(
            Sandbox sandbox,
            String providerState,
            SandboxGateway gateway,
            StoragePolicy policy) {
        return store.markSnapshotting(sandbox.id(), now())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-state-conflict",
                        "沙箱状态已变化，不能创建快照。")))
                .flatMap(snapshotting -> gateway
                        .persistWorkspace(sandbox.id(), providerState)
                        .flatMap(archive -> snapshots.store(snapshotting.toContract(), policy, archive)))
                .flatMap(snapshot -> store.finishSnapshot(sandbox.id(), now())
                        .flatMap(ready -> events
                                .publish(ready.id(), SandboxEvents.SNAPSHOT_CREATED, Map.of(
                                        "snapshotId", snapshot.snapshotId(),
                                        "storagePolicyId", snapshot.storagePolicyId(),
                                        "sizeBytes", snapshot.sizeBytes()))
                                .thenReturn(snapshot)))
                .onErrorResume(error -> store.finishSnapshot(sandbox.id(), now())
                        .onErrorResume(ignored -> Mono.empty())
                        .then(Mono.error(failure(error, "sandbox-snapshot-failed"))));
    }

    private Mono<Sandbox> performStop(
            SandboxStore.SessionRow sandbox, SandboxGateway gateway) {
        return gateway
                .stop(sandbox.id(), sandbox.providerState())
                .then(store.markStopped(sandbox.id(), now()))
                .switchIfEmpty(Mono.defer(() -> store.findById(sandbox.id())))
                .flatMap(stopped -> events
                        .publish(stopped.id(), SandboxEvents.STOPPED, Map.of(
                                "status", stopped.status(),
                                "stoppedAt", stopped.stoppedAt()))
                        .thenReturn(stopped.toContract()))
                .onErrorResume(error -> Mono.error(failure(error, "sandbox-stop-failed")));
    }

    private Mono<Sandbox> performResume(
            SandboxStore.SessionRow sandbox, SandboxGateway gateway) {
        var resumingAt = now();
        var expiresAt = resumingAt.plusSeconds(properties.getDefaultTtl().toSeconds());
        return store.markResuming(sandbox.id(), resumingAt)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-state-conflict",
                        "沙箱状态已变化，不能恢复。")))
                .flatMap(resuming -> gateway
                        .resume(new SandboxGateway.ResumeCommand(
                                sandbox.id(),
                                sandbox.providerState(),
                                sandbox.template(),
                                sandbox.region()))
                        .flatMap(resumed -> store.markResumedReady(
                                sandbox.id(),
                                resumed.providerSandboxId(),
                                resumed.providerState(),
                                resumed.workspaceRoot(),
                                expiresAt,
                                now()))
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "sandbox-state-conflict",
                                "沙箱状态已变化，不能完成恢复。")))
                        .flatMap(ready -> events
                                .publish(ready.id(), SandboxEvents.RESUMED, Map.of(
                                        "status", ready.status(),
                                        "expiresAt", ready.expiresAt(),
                                        "workspaceRoot", ready.workspaceRoot()))
                                .thenReturn(ready.toContract()))
                        .onErrorResume(error -> failResume(sandbox.id(), error)));
    }

    private Mono<Sandbox> failResume(UUID sandboxId, Throwable error) {
        var failure = failure(error, "sandbox-resume-failed");
        return store.markFailed(sandboxId, failure.getCode(), reason(failure), now())
                .flatMap(failed -> events
                        .publish(failed.id(), SandboxEvents.FAILED, Map.of(
                                "status", failed.status(),
                                "errorCode", failure.getCode(),
                                "errorMessage", reason(failure)))
                        .thenReturn(failed))
                .then(Mono.error(failure));
    }

    private Mono<SandboxFileResult> performUpload(
            SandboxStore.SessionRow sandbox,
            SandboxGateway gateway,
            String path,
            byte[] content,
            int size,
            String sha256) {
        var startedAt = now();
        return store.insertOperation(
                        sandbox.id(),
                        OPERATION_UPLOAD,
                        path,
                        properties.normalizedDefaultExecTimeoutSeconds(),
                        startedAt)
                .flatMap(operation -> gateway
                        .upload(new SandboxGateway.UploadCommand(
                                sandbox.id(),
                                sandbox.providerState(),
                                sandbox.template(),
                                sandbox.region(),
                                path,
                                content,
                                sha256))
                        .then(store.finishOperation(
                                operation.id(),
                                SandboxOperation.Status.SUCCEEDED,
                                null,
                                null,
                                null,
                                false,
                                null,
                                null,
                                now()))
                        .thenReturn(new SandboxFileResult(path, size, sha256))
                        .onErrorResume(error -> failFileOperation(
                                sandbox.toContract(), operation, error)));
    }

    private Mono<DownloadResult> performDownload(
            SandboxStore.SessionRow sandbox, SandboxGateway gateway, String path) {
        var startedAt = now();
        return store.insertOperation(
                        sandbox.id(),
                        OPERATION_DOWNLOAD,
                        path,
                        properties.normalizedDefaultExecTimeoutSeconds(),
                        startedAt)
                .flatMap(operation -> gateway
                        .download(new SandboxGateway.DownloadCommand(
                                sandbox.id(),
                                sandbox.providerState(),
                                sandbox.template(),
                                sandbox.region(),
                                path))
                        .flatMap(downloaded -> {
                            if (downloaded.content().length > properties.normalizedMaxFileBytes()) {
                                return Mono.error(new ApiException(
                                        HttpStatus.PAYLOAD_TOO_LARGE,
                                        "sandbox-file-too-large",
                                        "沙箱文件大小超过限制。"));
                            }
                            return store.finishOperation(
                                            operation.id(),
                                            SandboxOperation.Status.SUCCEEDED,
                                            null,
                                            null,
                                            null,
                                            false,
                                            null,
                                            null,
                                            now())
                                    .thenReturn(new DownloadResult(
                                            downloaded.content(),
                                            downloaded.sha256(),
                                            fileName(path)));
                        })
                        .onErrorResume(error -> failFileOperation(
                                sandbox.toContract(), operation, error)));
    }

    private <T> Mono<T> failFileOperation(
            Sandbox sandbox, SandboxOperation operation, Throwable error) {
        var failure = failure(error, "sandbox-file-failed");
        return store.finishOperation(
                        operation.id(),
                        SandboxOperation.Status.FAILED,
                        null,
                        null,
                        null,
                        false,
                        failure.getCode(),
                        reason(failure),
                        now())
                .flatMap(finished -> events
                        .publish(
                                sandbox.id(),
                                SandboxEvents.OPERATION_FINISHED,
                                Map.of(
                                        "operationId", operation.id(),
                                        "status", SandboxOperation.Status.FAILED.getCode(),
                                        "errorCode", failure.getCode()))
                        .thenReturn(true))
                .then(Mono.error(failure));
    }

    private Mono<Void> performDelete(
            SandboxStore.SessionRow sandbox, SandboxGateway gateway) {
        var destroy = hasProviderState(sandbox.providerState())
                ? gateway.destroy(sandbox.id(), sandbox.providerState())
                : Mono.<Void>empty();
        return destroy
                .then(store.markCancelled(sandbox.id(), now()))
                .switchIfEmpty(Mono.defer(() -> store.findById(sandbox.id())))
                .flatMap(cancelled -> events
                        .publish(cancelled.id(), SandboxEvents.STOPPED, Map.of(
                                "status", cancelled.status(),
                                "reason", "deleted"))
                        .thenReturn(cancelled))
                .then()
                .onErrorResume(error -> Mono.error(failure(error, "sandbox-delete-failed")));
    }

    private Mono<SandboxStore.SessionRow> expireIfNeeded(SandboxStore.SessionRow sandbox) {
        if (sandbox.status() == Sandbox.Status.EXPIRED
                || sandbox.status().terminal()
                || sandbox.expiresAt() == null
                || sandbox.expiresAt().isAfter(now())) {
            return Mono.just(sandbox);
        }
        return store.markExpired(sandbox.id(), now())
                .flatMap(expired -> events
                        .publish(expired.id(), SandboxEvents.EXPIRED, Map.of(
                                "status", expired.status(),
                                "expiresAt", expired.expiresAt()))
                        .thenReturn(expired))
                .switchIfEmpty(Mono.defer(() -> store.findById(sandbox.id())))
                .switchIfEmpty(Mono.just(sandbox));
    }

    private Mono<SandboxGateway> gateway(String provider) {
        var gateway = gateways.find(provider).orElse(null);
        if (gateway == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-provider-unsupported",
                    "当前没有该沙箱供应商的真实实现。"));
        }
        if (!gateway.configured()) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "sandbox-provider-not-configured",
                    "沙箱供应商尚未配置。"));
        }
        return Mono.just(gateway);
    }

    private <T> Mono<T> withLease(UUID sandboxId, Mono<T> operation) {
        return leases
                .acquire(sandboxId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "sandbox-busy",
                        "沙箱正在执行其他操作。")))
                .flatMap(lease -> Mono.usingWhen(
                        Mono.just(lease),
                        ignored -> operation,
                        ignored -> release(lease),
                        (ignored, error) -> release(lease),
                        ignored -> release(lease)));
    }

    private Mono<Void> release(TokenLease lease) {
        return leases.release(lease).then().onErrorResume(error -> {
            log.warn("释放沙箱租约失败：{}", lease.key(), error);
            return Mono.empty();
        });
    }

    private static boolean hasProviderState(String providerState) {
        return StringUtils.hasText(providerState) && !"{}".equals(providerState.trim());
    }

    private ApiException failure(Throwable error, String defaultCode) {
        if (error instanceof ApiException api) {
            return api;
        }
        if (error instanceof SandboxGateway.Failure gatewayFailure) {
            var status = switch (gatewayFailure.code()) {
                case "sandbox-provider-not-configured" -> HttpStatus.SERVICE_UNAVAILABLE;
                case "sandbox-file-not-found" -> HttpStatus.NOT_FOUND;
                case "sandbox-file-too-large" -> HttpStatus.PAYLOAD_TOO_LARGE;
                case "sandbox-state-missing" -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            var code = switch (gatewayFailure.code()) {
                case "sandbox-file-not-found",
                        "sandbox-file-too-large",
                        "sandbox-state-missing",
                        "sandbox-file-integrity",
                        "sandbox-file-output-truncated" -> gatewayFailure.code();
                default -> "sandbox-provider-not-configured".equals(gatewayFailure.code())
                        ? "sandbox-provider-not-configured"
                        : defaultCode;
            };
            return new ApiException(
                    status,
                    code,
                    "沙箱供应商调用失败。",
                    gatewayFailure);
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, defaultCode, "沙箱操作失败。", error);
    }

    private static String reason(ApiException failure) {
        return StringUtils.hasText(failure.getReason()) ? failure.getReason() : "沙箱操作失败。";
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        var maxBytes = properties.normalizedMaxOutputBytes();
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        var end = value.length();
        while (end > 0 && value.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String nextCursor(List<Sandbox> items, int limit) {
        if (items.size() < limit || items.isEmpty()) {
            return null;
        }
        var last = items.getLast();
        return CreationCursor.encode(last.createdAt(), last.id());
    }

    private static String nextOperationCursor(List<SandboxOperation> items, int limit) {
        if (items.size() < limit || items.isEmpty()) {
            return null;
        }
        var last = items.getLast();
        return CreationCursor.encode(last.startedAt(), last.id());
    }

    private static String fileName(String path) {
        try {
            var name = Path.of(path).getFileName();
            return name == null ? "download" : name.toString();
        } catch (InvalidPathException error) {
            return "download";
        }
    }

    private boolean matches(
            SandboxStore.SessionRow existing, SandboxCreateRequest request) {
        var ttlSeconds = Duration.between(existing.createdAt(), existing.expiresAt()).toSeconds();
        return Objects.equals(existing.agentSessionId(), request.agentSessionId())
                && Objects.equals(existing.runId(), request.runId())
                && existing.provider().equalsIgnoreCase(normalized(request.provider()))
                && Objects.equals(existing.template(), template(request, existing.provider()))
                && Objects.equals(existing.region(), region(request, existing.provider()))
                && ttlSeconds == ttlSeconds(request);
    }

    private String template(SandboxCreateRequest request, String provider) {
        return StringUtils.hasText(request.template())
                ? request.template().trim()
                : normalized(properties.defaultTemplate(provider));
    }

    private String region(SandboxCreateRequest request, String provider) {
        if (properties.isE2b(provider)) {
            return null;
        }
        return StringUtils.hasText(request.region())
                ? request.region().trim()
                : normalized(properties.defaultRegion(provider));
    }

    private long ttlSeconds(SandboxCreateRequest request) {
        return request.ttlSeconds() == null
                ? properties.getDefaultTtl().toSeconds()
                : request.ttlSeconds();
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static UUID idempotentId(RequestIdentity identity, String idempotencyKey) {
        var hash = Digests.sha256Hex(
                identity.ownerId() + '\0' + identity.workspaceId() + '\0' + idempotencyKey);
        return UUID.fromString(hash.substring(0, 8)
                + "-"
                + hash.substring(8, 12)
                + "-5"
                + hash.substring(13, 16)
                + "-8"
                + hash.substring(17, 20)
                + "-"
                + hash.substring(20, 32));
    }

    private static ApiException validateIdempotency(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-idempotency-key-missing",
                    "缺少 Idempotency-Key 请求头。");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-idempotency-key-invalid",
                    "幂等键长度不能超过 255。");
        }
        return null;
    }

    private static ApiException requestRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "sandbox-create-request-required", "创建请求不能为空。");
    }

    private static ApiException idempotencyConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "sandbox-idempotency-key-conflict",
                "幂等键已被其他请求参数使用。");
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "sandbox-not-found", "沙箱不存在。");
    }

    private static ApiException notReady() {
        return new ApiException(
                HttpStatus.CONFLICT, "sandbox-not-ready", "沙箱当前状态不能执行该操作。");
    }

    private static ApiException resumeNotAllowed() {
        return new ApiException(
                HttpStatus.CONFLICT, "sandbox-resume-not-allowed", "沙箱当前状态不能恢复。");
    }

    private static ApiException operationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "sandbox-operation-not-found", "沙箱操作不存在。");
    }

    private Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
