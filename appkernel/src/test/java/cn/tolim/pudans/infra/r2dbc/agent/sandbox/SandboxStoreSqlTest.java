package cn.tolim.pudans.infra.r2dbc.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.SandboxOperation;
import cn.tolim.pudans.contract.agent.SandboxSnapshot;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SandboxStoreSqlTest {

    private static final String SESSION_JSON_ALIAS =
            "provider_state_jsonb::text AS provider_state_json";

    @Mock
    DatabaseClient databaseClient;

    private final List<String> queries = new ArrayList<>();

    private SandboxStore store;

    private DatabaseClient.GenericExecuteSpec executeSpec;

    @BeforeEach
    void setUp() {
        store = new SandboxStore(databaseClient);
        executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        lenient().when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        lenient().when(executeSpec.bindNull(anyString(), any(Class.class))).thenReturn(executeSpec);
        lenient().when(executeSpec.map(ArgumentMatchers
                        .<BiFunction<Row, RowMetadata, Object>>any()))
                .thenReturn(new EmptyRows<>());
        when(databaseClient.sql(anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return executeSpec;
        });
    }

    @Test
    void everySessionQueryReadsProviderStateAsText() {
        var id = UUID.randomUUID();
        var cursor = new CreationCursor.Position(
                Instant.parse("2026-11-25T08:00:00Z"), UUID.randomUUID());

        assertSql(
                () -> store.findById(id),
                "SELECT",
                SESSION_JSON_ALIAS,
                "FROM sandbox_sessions");
        assertSql(
                () -> store.findOwned(id, "alice", "workspace-a"),
                "WHERE id = :id AND owner_id = :ownerId AND workspace_id = :workspaceId",
                SESSION_JSON_ALIAS);
        assertSql(
                () -> store.listMine("alice", "workspace-a", cursor, 20),
                "(created_at, id) < (:cursorTime, :cursorId)",
                "ORDER BY created_at DESC, id DESC LIMIT :limit",
                SESSION_JSON_ALIAS);
        assertSql(
                () -> store.markProvisioning(id, Instant.now()),
                "SET status = 'provisioning'",
                SESSION_JSON_ALIAS);
        assertSql(
                () -> store.markReady(id, "provider-1", "{}", "/workspace", Instant.now()),
                "provider_state_jsonb = CAST(:providerState AS jsonb)",
                "status IN ('requested', 'provisioning')",
                SESSION_JSON_ALIAS);
        assertSql(
                () -> store.markExpired(id, Instant.now()),
                "expires_at <= :now",
                "status NOT IN ('stopped', 'expired', 'failed', 'cancelled')",
                SESSION_JSON_ALIAS);
    }

    @Test
    void insertsSessionsAndOperationsWithStableStateTransitions() {
        var row = sessionRow(Sandbox.Status.REQUESTED, "{}", null);

        assertSql(
                () -> store.insert(row),
                "INSERT INTO sandbox_sessions",
                "CAST(:providerState AS jsonb)",
                "ON CONFLICT (id) DO NOTHING",
                SESSION_JSON_ALIAS);
        assertSql(
                () -> store.insertOperation(
                        row.id(), "exec", "pwd", 30, Instant.now()),
                "INSERT INTO sandbox_operations",
                "output_truncated",
                "RETURNING");
        assertSql(
                () -> store.finishOperation(
                        UUID.randomUUID(),
                        SandboxOperation.Status.SUCCEEDED,
                        0,
                        null,
                        null,
                        false,
                        null,
                        null,
                        Instant.now()),
                "UPDATE sandbox_operations",
                "WHERE id = :id AND status = 'running'",
                "RETURNING");
    }

    @Test
    void insertsSnapshotsWithTheExplicitRouteMetadata() {
        var snapshot = new SandboxSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "agentrun",
                "snapshot-1",
                "s3",
                "sandbox_snapshot",
                "snapshot-policy",
                "sandbox/snapshot.tar",
                42L,
                "checksum",
                SandboxSnapshot.Status.READY,
                Instant.now(),
                Instant.now().plusSeconds(60));

        assertSql(
                () -> store.insertSnapshot(snapshot),
                "INSERT INTO sandbox_snapshots",
                "storage_route_purpose",
                "RETURNING");
    }

    @Test
    void mapsSessionRowsWithoutExposingProviderStateInTheContract() {
        var id = UUID.randomUUID();
        var row = mock(Row.class);
        var metadata = mock(RowMetadata.class);
        var now = Instant.parse("2026-11-25T08:00:00Z");
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("owner_id", String.class)).thenReturn("alice");
        when(row.get("workspace_id", String.class)).thenReturn("workspace-a");
        when(row.get("agent_session_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.get("run_id", UUID.class)).thenReturn(null);
        when(row.get("provider", String.class)).thenReturn("agentrun");
        when(row.get("template", String.class)).thenReturn("default");
        when(row.get("region", String.class)).thenReturn(null);
        when(row.get("status", String.class)).thenReturn("ready");
        when(row.get("workspace_root", String.class)).thenReturn("/workspace");
        when(row.get("provider_sandbox_id", String.class)).thenReturn(null);
        when(row.get("provider_state_json", String.class)).thenReturn("{\"sandboxId\":\"provider-1\"}");
        when(row.get("expires_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.ofInstant(
                        now.plusSeconds(600), java.time.ZoneOffset.UTC));
        when(row.get("last_heartbeat_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(row.get("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
        when(row.get("started_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(row.get("stopped_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(row.get("error_code", String.class)).thenReturn(null);
        when(row.get("error_message", String.class)).thenReturn(null);
        var mapper = new AtomicReference<BiFunction<Row, RowMetadata, SandboxStore.SessionRow>>();
        var rows = mock(RowsFetchSpec.class);
        when(executeSpec.map(ArgumentMatchers
                        .<BiFunction<Row, RowMetadata, SandboxStore.SessionRow>>any()))
                .thenAnswer(invocation -> {
                    mapper.set(invocation.getArgument(0));
                    return rows;
                });
        when(rows.one()).thenReturn(Mono.defer(
                () -> Mono.just(mapper.get().apply(row, metadata))));

        var mapped = store.findById(id).block();

        assertThat(mapped).isNotNull();
        assertThat(mapped.status()).isEqualTo(Sandbox.Status.READY);
        assertThat(mapped.providerState()).contains("provider-1");
        assertThat(mapped.toContract().toString()).doesNotContain("provider-1", "providerState");
    }

    private void assertSql(Runnable operation, String... fragments) {
        var before = queries.size();
        operation.run();
        assertThat(queries).hasSize(before + 1);
        assertThat(queries.getLast()).contains(fragments);
    }

    private static SandboxStore.SessionRow sessionRow(
            Sandbox.Status status, String providerState, UUID runId) {
        var now = Instant.parse("2026-11-25T08:00:00Z");
        return new SandboxStore.SessionRow(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                runId,
                "agentrun",
                "default",
                "cn-shanghai",
                status,
                "/workspace",
                null,
                providerState,
                now.plusSeconds(600),
                now,
                now,
                null,
                null,
                null,
                null);
    }

    private record EmptyRows<T>() implements RowsFetchSpec<T> {

        @Override
        public Mono<T> one() {
            return Mono.empty();
        }

        @Override
        public Mono<T> first() {
            return Mono.empty();
        }

        @Override
        public Flux<T> all() {
            return Flux.empty();
        }
    }

}
