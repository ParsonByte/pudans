package cn.tolim.pudans.infra.r2dbc.agent.sandbox;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class SandboxStore {

    private static final String SESSION_COLUMNS = """
            id, owner_id, workspace_id, agent_session_id, run_id, provider, template, region,
            status, workspace_root, provider_sandbox_id,
            provider_state_jsonb::text AS provider_state_json,
            expires_at, last_heartbeat_at, created_at, started_at, stopped_at,
            error_code, error_message
            """;

    private static final String OPERATION_COLUMNS = """
            id, sandbox_id, operation_type, command, status, exit_code, stdout_ref, stderr_ref,
            output_truncated, approval_id, timeout_seconds, started_at, finished_at,
            error_code, error_message
            """;

    private static final String SNAPSHOT_COLUMNS = """
            id, sandbox_id, provider, snapshot_id, storage_kind, storage_route_purpose,
            storage_policy_id, object_key, size_bytes, checksum, status, created_at, expires_at
            """;

    private static final String SELECT_SESSION =
            "SELECT " + SESSION_COLUMNS + " FROM sandbox_sessions ";

    private static final String INSERT_SESSION = """
            INSERT INTO sandbox_sessions (
                id, owner_id, workspace_id, agent_session_id, run_id, provider, template, region,
                status, workspace_root, provider_sandbox_id, provider_state_jsonb,
                expires_at, last_heartbeat_at, created_at, started_at, stopped_at,
                error_code, error_message)
            VALUES (
                :id, :ownerId, :workspaceId, :agentSessionId, :runId, :provider, :template, :region,
                :status, :workspaceRoot, :providerSandboxId, CAST(:providerState AS jsonb),
                :expiresAt, :lastHeartbeatAt, :createdAt, :startedAt, :stoppedAt,
                :errorCode, :errorMessage)
            ON CONFLICT (id) DO NOTHING
            RETURNING
            """ + SESSION_COLUMNS;

    private static final String INSERT_OPERATION = """
            INSERT INTO sandbox_operations (
                id, sandbox_id, operation_type, command, status, exit_code, stdout_ref, stderr_ref,
                output_truncated, approval_id, timeout_seconds, started_at, finished_at,
                error_code, error_message)
            VALUES (
                :id, :sandboxId, :operationType, :command, :status, :exitCode, :stdoutRef, :stderrRef,
                :outputTruncated, :approvalId, :timeoutSeconds, :startedAt, :finishedAt,
                :errorCode, :errorMessage)
            RETURNING
            """ + OPERATION_COLUMNS;

    private static final String INSERT_SNAPSHOT = """
            INSERT INTO sandbox_snapshots (
                id, sandbox_id, provider, snapshot_id, storage_kind, storage_route_purpose,
                storage_policy_id, object_key, size_bytes, checksum, status, created_at, expires_at)
            VALUES (
                :id, :sandboxId, :provider, :snapshotId, :storageKind, :storageRoutePurpose,
                :storagePolicyId, :objectKey, :sizeBytes, :checksum, :status, :createdAt, :expiresAt)
            RETURNING
            """ + SNAPSHOT_COLUMNS;

    private final DatabaseClient databaseClient;

    public SandboxStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public record Insertion(SessionRow session, boolean created) {}

    public record SessionRow(
            UUID id,
            String ownerId,
            String workspaceId,
            UUID agentSessionId,
            UUID runId,
            String provider,
            String template,
            String region,
            Sandbox.Status status,
            String workspaceRoot,
            String providerSandboxId,
            String providerState,
            Instant expiresAt,
            Instant lastHeartbeatAt,
            Instant createdAt,
            Instant startedAt,
            Instant stoppedAt,
            String errorCode,
            String errorMessage) {

        // 对外视图不暴露供应商沙箱标识和内部状态。
        public Sandbox toContract() {
            return new Sandbox(
                    id,
                    ownerId,
                    workspaceId,
                    agentSessionId,
                    runId,
                    provider,
                    template,
                    region,
                    status,
                    workspaceRoot,
                    expiresAt,
                    lastHeartbeatAt,
                    createdAt,
                    startedAt,
                    stoppedAt,
                    errorCode,
                    errorMessage);
        }
    }

    public Mono<Insertion> insert(SessionRow session) {
        var spec = databaseClient
                .sql(INSERT_SESSION)
                .bind("id", session.id())
                .bind("ownerId", session.ownerId())
                .bind("workspaceId", session.workspaceId())
                .bind("agentSessionId", session.agentSessionId())
                .bind("provider", session.provider())
                .bind("template", session.template())
                .bind("status", session.status().getCode())
                .bind("expiresAt", offset(session.expiresAt()))
                .bind("createdAt", offset(session.createdAt()));
        spec = bindNullable(spec, "runId", session.runId(), UUID.class);
        spec = bindNullable(spec, "region", session.region(), String.class);
        spec = bindNullable(spec, "workspaceRoot", session.workspaceRoot(), String.class);
        spec = bindNullable(
                spec, "providerSandboxId", session.providerSandboxId(), String.class);
        spec = bindNullable(
                spec, "lastHeartbeatAt", offset(session.lastHeartbeatAt()), OffsetDateTime.class);
        spec = bindNullable(spec, "startedAt", offset(session.startedAt()), OffsetDateTime.class);
        spec = bindNullable(spec, "stoppedAt", offset(session.stoppedAt()), OffsetDateTime.class);
        spec = bindNullable(spec, "errorCode", session.errorCode(), String.class);
        spec = bindNullable(spec, "errorMessage", session.errorMessage(), String.class);
        return spec.bind("providerState", session.providerState())
                .map(SandboxStore::session)
                .one()
                .map(row -> new Insertion(row, true))
                .switchIfEmpty(Mono.defer(() -> findById(session.id())
                        .map(row -> new Insertion(row, false))));
    }

    public Mono<SessionRow> findById(UUID id) {
        return databaseClient
                .sql(SELECT_SESSION + "WHERE id = :id")
                .bind("id", id)
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> findOwned(UUID id, String ownerId, String workspaceId) {
        return databaseClient
                .sql(SELECT_SESSION
                        + """
                          WHERE id = :id AND owner_id = :ownerId AND workspace_id = :workspaceId
                          """)
                .bind("id", id)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .map(SandboxStore::session)
                .one();
    }

    public Flux<SessionRow> listMine(
            String ownerId,
            String workspaceId,
            CreationCursor.Position cursor,
            int limit) {
        return list(ownerId, workspaceId, cursor, limit);
    }

    public Flux<SessionRow> listAll(CreationCursor.Position cursor, int limit) {
        return list(null, null, cursor, limit);
    }

    private Flux<SessionRow> list(
            String ownerId,
            String workspaceId,
            CreationCursor.Position cursor,
            int limit) {
        var sql = SELECT_SESSION
                + """
                  WHERE (CAST(:ownerId AS varchar) IS NULL
                      OR (owner_id = :ownerId AND workspace_id = :workspaceId))
                  """;
        if (cursor != null) {
            sql += """
                    AND (created_at, id) < (:cursorTime, :cursorId)
                    """;
        }
        sql += " ORDER BY created_at DESC, id DESC LIMIT :limit";
        var spec = databaseClient.sql(sql);
        spec = ownerId == null
                ? spec.bindNull("ownerId", String.class).bindNull("workspaceId", String.class)
                : spec.bind("ownerId", ownerId).bind("workspaceId", workspaceId);
        if (cursor != null) {
            spec = spec.bind("cursorTime", offset(cursor.sortTime()))
                    .bind("cursorId", cursor.id());
        }
        return spec.bind("limit", limit).map(SandboxStore::session).all();
    }

    public Flux<SandboxOperation> listOperations(
            UUID sandboxId, CreationCursor.Position cursor, int limit) {
        var sql = "SELECT "
                + OPERATION_COLUMNS
                + " FROM sandbox_operations WHERE sandbox_id = :sandboxId";
        if (cursor != null) {
            sql += " AND (started_at, id) < (:cursorTime, :cursorId)";
        }
        sql += " ORDER BY started_at DESC, id DESC LIMIT :limit";
        var spec = databaseClient.sql(sql).bind("sandboxId", sandboxId);
        if (cursor != null) {
            spec = spec.bind("cursorTime", offset(cursor.sortTime()))
                    .bind("cursorId", cursor.id());
        }
        return spec.bind("limit", limit).map(SandboxStore::operation).all();
    }

    public Mono<SandboxOperation> findOperation(UUID sandboxId, UUID operationId) {
        return databaseClient
                .sql("SELECT "
                        + OPERATION_COLUMNS
                        + " FROM sandbox_operations "
                        + "WHERE sandbox_id = :sandboxId AND id = :operationId")
                .bind("sandboxId", sandboxId)
                .bind("operationId", operationId)
                .map(SandboxStore::operation)
                .one();
    }

    public Mono<SessionRow> markProvisioning(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'provisioning', last_heartbeat_at = :now
                        WHERE id = :id AND status = 'requested'
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markReady(
            UUID id,
            String providerSandboxId,
            String providerState,
            String workspaceRoot,
            Instant now) {
        var spec = databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'ready',
                            provider_sandbox_id = :providerSandboxId,
                            provider_state_jsonb = CAST(:providerState AS jsonb),
                            workspace_root = :workspaceRoot,
                            started_at = COALESCE(started_at, :now),
                            last_heartbeat_at = :now,
                            error_code = NULL,
                            error_message = NULL
                        WHERE id = :id AND status IN ('requested', 'provisioning')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("workspaceRoot", workspaceRoot)
                .bind("providerState", providerState)
                .bind("now", offset(now));
        spec = bindNullable(spec, "providerSandboxId", providerSandboxId, String.class);
        return spec.map(SandboxStore::session).one();
    }

    public Mono<SessionRow> markFailed(
            UUID id, String errorCode, String errorMessage, Instant now) {
        var spec = databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'failed',
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            last_heartbeat_at = :now
                        WHERE id = :id
                          AND status NOT IN ('stopped', 'expired', 'failed', 'cancelled')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now));
        spec = bindNullable(spec, "errorCode", errorCode, String.class);
        spec = bindNullable(spec, "errorMessage", errorMessage, String.class);
        return spec.map(SandboxStore::session).one();
    }

    public Mono<SessionRow> markRunning(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'running',
                            last_heartbeat_at = :now
                        WHERE id = :id AND status IN ('ready', 'running')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markSnapshotting(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'snapshotting', last_heartbeat_at = :now
                        WHERE id = :id AND status IN ('ready', 'running')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> finishSnapshot(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'ready', last_heartbeat_at = :now
                        WHERE id = :id AND status = 'snapshotting'
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markStopped(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'stopped',
                            stopped_at = :now,
                            last_heartbeat_at = :now
                        WHERE id = :id
                          AND status NOT IN ('stopped', 'expired', 'failed', 'cancelled')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markResuming(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'resuming',
                            stopped_at = NULL,
                            error_code = NULL,
                            error_message = NULL,
                            last_heartbeat_at = :now
                        WHERE id = :id AND status = 'stopped'
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markResumedReady(
            UUID id,
            String providerSandboxId,
            String providerState,
            String workspaceRoot,
            Instant expiresAt,
            Instant now) {
        var spec = databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'ready',
                            provider_sandbox_id = :providerSandboxId,
                            provider_state_jsonb = CAST(:providerState AS jsonb),
                            workspace_root = :workspaceRoot,
                            expires_at = :expiresAt,
                            started_at = COALESCE(started_at, :now),
                            stopped_at = NULL,
                            last_heartbeat_at = :now,
                            error_code = NULL,
                            error_message = NULL
                        WHERE id = :id AND status = 'resuming'
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("workspaceRoot", workspaceRoot)
                .bind("providerState", providerState)
                .bind("expiresAt", offset(expiresAt))
                .bind("now", offset(now));
        spec = bindNullable(spec, "providerSandboxId", providerSandboxId, String.class);
        return spec.map(SandboxStore::session).one();
    }

    public Mono<SessionRow> markCancelled(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'cancelled',
                            stopped_at = COALESCE(stopped_at, :now),
                            last_heartbeat_at = :now
                        WHERE id = :id
                          AND status NOT IN ('expired', 'cancelled')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SessionRow> markExpired(UUID id, Instant now) {
        return databaseClient
                .sql("""
                        UPDATE sandbox_sessions
                        SET status = 'expired',
                            stopped_at = COALESCE(stopped_at, :now),
                            last_heartbeat_at = :now
                        WHERE id = :id
                          AND expires_at <= :now
                          AND status NOT IN ('stopped', 'expired', 'failed', 'cancelled')
                        RETURNING
                        """ + SESSION_COLUMNS)
                .bind("id", id)
                .bind("now", offset(now))
                .map(SandboxStore::session)
                .one();
    }

    public Mono<SandboxOperation> insertOperation(
            UUID sandboxId,
            String operationType,
            String command,
            int timeoutSeconds,
            Instant startedAt) {
        var id = UUID.randomUUID();
        return databaseClient
                .sql(INSERT_OPERATION)
                .bind("id", id)
                .bind("sandboxId", sandboxId)
                .bind("operationType", operationType)
                .bind("command", command)
                .bind("status", SandboxOperation.Status.RUNNING.getCode())
                .bind("timeoutSeconds", timeoutSeconds)
                .bind("startedAt", offset(startedAt))
                .bind("outputTruncated", false)
                .bindNull("exitCode", Integer.class)
                .bindNull("stdoutRef", String.class)
                .bindNull("stderrRef", String.class)
                .bindNull("approvalId", UUID.class)
                .bindNull("finishedAt", OffsetDateTime.class)
                .bindNull("errorCode", String.class)
                .bindNull("errorMessage", String.class)
                .map(SandboxStore::operation)
                .one();
    }

    public Mono<SandboxOperation> finishOperation(
            UUID operationId,
            SandboxOperation.Status status,
            Integer exitCode,
            String stdoutRef,
            String stderrRef,
            boolean outputTruncated,
            String errorCode,
            String errorMessage,
            Instant finishedAt) {
        var spec = databaseClient
                .sql("""
                        UPDATE sandbox_operations
                        SET status = :status,
                            exit_code = :exitCode,
                            stdout_ref = :stdoutRef,
                            stderr_ref = :stderrRef,
                            output_truncated = :outputTruncated,
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            finished_at = :finishedAt
                        WHERE id = :id AND status = 'running'
                        RETURNING
                        """ + OPERATION_COLUMNS)
                .bind("id", operationId)
                .bind("status", status.getCode())
                .bind("outputTruncated", outputTruncated)
                .bind("finishedAt", offset(finishedAt));
        spec = bindNullable(spec, "exitCode", exitCode, Integer.class);
        spec = bindNullable(spec, "stdoutRef", stdoutRef, String.class);
        spec = bindNullable(spec, "stderrRef", stderrRef, String.class);
        spec = bindNullable(spec, "errorCode", errorCode, String.class);
        spec = bindNullable(spec, "errorMessage", errorMessage, String.class);
        return spec.map(SandboxStore::operation).one();
    }

    public Mono<SandboxSnapshot> insertSnapshot(SandboxSnapshot snapshot) {
        var spec = databaseClient
                .sql(INSERT_SNAPSHOT)
                .bind("id", snapshot.id())
                .bind("sandboxId", snapshot.sandboxId())
                .bind("provider", snapshot.provider())
                .bind("storageKind", snapshot.storageKind())
                .bind("storageRoutePurpose", snapshot.storageRoutePurpose())
                .bind("storagePolicyId", snapshot.storagePolicyId())
                .bind("objectKey", snapshot.objectKey())
                .bind("status", snapshot.status().getCode())
                .bind("createdAt", offset(snapshot.createdAt()));
        spec = bindNullable(spec, "snapshotId", snapshot.snapshotId(), String.class);
        spec = bindNullable(spec, "sizeBytes", snapshot.sizeBytes(), Long.class);
        spec = bindNullable(spec, "checksum", snapshot.checksum(), String.class);
        spec = bindNullable(
                spec, "expiresAt", offset(snapshot.expiresAt()), OffsetDateTime.class);
        return spec.map(SandboxStore::snapshot).one();
    }

    private static SessionRow session(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new SessionRow(
                row.get("id", UUID.class),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("agent_session_id", UUID.class),
                row.get("run_id", UUID.class),
                row.get("provider", String.class),
                row.get("template", String.class),
                row.get("region", String.class),
                Sandbox.Status.fromCode(row.get("status", String.class)),
                row.get("workspace_root", String.class),
                row.get("provider_sandbox_id", String.class),
                row.get("provider_state_json", String.class),
                instant(row, "expires_at"),
                instant(row, "last_heartbeat_at"),
                instant(row, "created_at"),
                instant(row, "started_at"),
                instant(row, "stopped_at"),
                row.get("error_code", String.class),
                row.get("error_message", String.class));
    }

    private static SandboxOperation operation(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new SandboxOperation(
                row.get("id", UUID.class),
                row.get("sandbox_id", UUID.class),
                row.get("operation_type", String.class),
                row.get("command", String.class),
                SandboxOperation.Status.fromCode(row.get("status", String.class)),
                row.get("exit_code", Integer.class),
                row.get("stdout_ref", String.class),
                row.get("stderr_ref", String.class),
                Boolean.TRUE.equals(row.get("output_truncated", Boolean.class)),
                row.get("approval_id", UUID.class),
                intValue(row.get("timeout_seconds", Integer.class)),
                instant(row, "started_at"),
                instant(row, "finished_at"),
                row.get("error_code", String.class),
                row.get("error_message", String.class));
    }

    private static SandboxSnapshot snapshot(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new SandboxSnapshot(
                row.get("id", UUID.class),
                row.get("sandbox_id", UUID.class),
                row.get("provider", String.class),
                row.get("snapshot_id", String.class),
                row.get("storage_kind", String.class),
                row.get("storage_route_purpose", String.class),
                row.get("storage_policy_id", String.class),
                row.get("object_key", String.class),
                row.get("size_bytes", Long.class),
                row.get("checksum", String.class),
                SandboxSnapshot.Status.fromCode(row.get("status", String.class)),
                instant(row, "created_at"),
                instant(row, "expires_at"));
    }

    private static Instant instant(io.r2dbc.spi.Row row, String column) {
        var value = row.get(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
