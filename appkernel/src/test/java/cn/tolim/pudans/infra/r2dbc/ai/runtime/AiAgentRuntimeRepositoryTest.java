package cn.tolim.pudans.infra.r2dbc.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.infra.r2dbc.ai.runtime.AiAgentRuntimeRepository.RuntimeRecord;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiAgentRuntimeRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    RowsFetchSpec<RuntimeRecord> fetchSpec;

    @Mock
    Row row;

    private AiAgentRuntimeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiAgentRuntimeRepository(databaseClient);
    }

    @Test
    void mapsARuntimeRow() {
        when(row.get("agent_name", String.class)).thenReturn("agent-1");
        when(row.get("provider", String.class)).thenReturn("TENCENT");
        when(row.get("sandbox_id", String.class)).thenReturn("ins-1");
        when(row.get("state", String.class)).thenReturn("RUNNING");
        when(row.get("image", String.class)).thenReturn("image-agent");
        when(row.get("entrypoint", String.class)).thenReturn("python app.py");
        when(row.get("cpu", Double.class)).thenReturn(2.0);
        when(row.get("memory_mb", Integer.class)).thenReturn(2048);
        when(row.get("timeout_seconds", Integer.class)).thenReturn(600);
        when(row.get("endpoint", String.class)).thenReturn("https://49983-ins-1.ap-guangzhou.tencentags.com");
        when(row.get("last_error", String.class)).thenReturn(null);
        when(row.get("config", String.class)).thenReturn("{\"cpu\":2.0}");
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("updated_at", Instant.class)).thenReturn(NOW);
        when(row.get("last_active_at", Instant.class)).thenReturn(NOW);

        var record = AiAgentRuntimeRepository.mapRow(row);

        assertThat(record.agentName()).isEqualTo("agent-1");
        assertThat(record.provider()).isEqualTo(AiSandboxProvider.Provider.TENCENT);
        assertThat(record.sandboxId()).isEqualTo("ins-1");
        assertThat(record.state()).isEqualTo(SandboxState.RUNNING);
        assertThat(record.image()).isEqualTo("image-agent");
        assertThat(record.entrypoint()).isEqualTo("python app.py");
        assertThat(record.cpu()).isEqualTo(2.0);
        assertThat(record.memoryMb()).isEqualTo(2048);
        assertThat(record.timeoutSeconds()).isEqualTo(600);
        assertThat(record.endpoint()).isEqualTo("https://49983-ins-1.ap-guangzhou.tencentags.com");
        assertThat(record.lastError()).isNull();
        assertThat(record.config()).isEqualTo("{\"cpu\":2.0}");
        assertThat(record.createdAt()).isEqualTo(NOW);
        assertThat(record.updatedAt()).isEqualTo(NOW);
        assertThat(record.lastActiveAt()).isEqualTo(NOW);
    }

    @Test
    void upsertsTheRuntimeWithJsonConfigAndReturnsTheStoredRow() {
        stubSqlExecution();
        when(spec.bindNull(anyString(), any())).thenReturn(spec);
        var stored = record(SandboxState.CREATING);
        when(fetchSpec.one()).thenReturn(Mono.just(stored));

        StepVerifier.create(repository.upsert(stored)).expectNext(stored).verifyComplete();

        var sql = capturedSql();
        assertThat(sql)
                .contains("INSERT INTO ai_agent_runtime")
                .contains("ON CONFLICT (agent_name) DO UPDATE")
                .contains("config = EXCLUDED.config")
                .contains("updated_at = now()")
                .contains("RETURNING agent_name, provider, sandbox_id, state");
        verify(spec).bind("agentName", "agent-1");
        verify(spec).bind("provider", "ALIYUN");
        verify(spec).bind("state", "CREATING");
        verify(spec).bind("sandboxId", "sb-1");
        verify(spec).bind("image", "image-agent");
        verify(spec).bind("cpu", 1.0);
        verify(spec).bind("memoryMb", 1024);
        verify(spec).bind("timeoutSeconds", 3600);
        verify(spec).bind("config", "{\"cpu\":1.0}");
        verify(spec).bindNull("lastError", String.class);
    }

    @Test
    void updateStateBindsOptionalColumnsAsNullAndTouchesOnlyWhenRequested() {
        stubSqlExecution();
        when(spec.bindNull(anyString(), any())).thenReturn(spec);
        when(fetchSpec.one()).thenReturn(Mono.just(record(SandboxState.STOPPED)));

        StepVerifier.create(repository.updateState("agent-1", SandboxState.STOPPED, null, null, null, true))
                .expectNextCount(1)
                .verifyComplete();

        var sql = capturedSql();
        assertThat(sql)
                .contains("UPDATE ai_agent_runtime")
                .contains("last_active_at = CASE WHEN :touch THEN now() ELSE last_active_at END")
                .contains("updated_at = now()")
                .contains("RETURNING agent_name, provider, sandbox_id, state");
        verify(spec).bind("state", "STOPPED");
        verify(spec).bind("touch", true);
        verify(spec).bindNull("sandboxId", String.class);
        verify(spec).bindNull("endpoint", String.class);
        verify(spec).bindNull("lastError", String.class);
        verify(spec).bind("agentName", "agent-1");
    }

    @Test
    void findsARuntimeByAgentName() {
        stubSqlExecution();
        var stored = record(SandboxState.RUNNING);
        when(fetchSpec.one()).thenReturn(Mono.just(stored));

        StepVerifier.create(repository.find("agent-1")).expectNext(stored).verifyComplete();

        assertThat(capturedSql())
                .contains("FROM ai_agent_runtime")
                .contains("WHERE agent_name = :agentName");
        verify(spec).bind("agentName", "agent-1");
    }

    @Test
    void listsRuntimesBeforeAMillisecondCursor() {
        stubSqlExecution();
        var stored = record(SandboxState.RUNNING);
        when(fetchSpec.all()).thenReturn(Flux.just(stored));

        StepVerifier.create(repository.listBefore(123L, 20)).expectNext(stored).verifyComplete();

        assertThat(capturedSql())
                .contains("EXTRACT(EPOCH FROM updated_at) * 1000 < :beforeMillis")
                .contains("ORDER BY updated_at DESC, agent_name DESC")
                .contains("LIMIT :limit");
        verify(spec).bind("beforeMillis", 123L);
        verify(spec).bind("limit", 20);
    }

    private void stubSqlExecution() {
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
    }

    private String capturedSql() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(captor.capture());
        return captor.getValue();
    }

    private static RuntimeRecord record(SandboxState state) {
        return new RuntimeRecord(
                "agent-1",
                AiSandboxProvider.Provider.ALIYUN,
                "sb-1",
                state,
                "image-agent",
                "python app.py",
                1.0,
                1024,
                3600,
                "https://sandbox.example.com",
                null,
                "{\"cpu\":1.0}",
                NOW,
                NOW,
                NOW);
    }
}