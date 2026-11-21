package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryExportRow;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiMemoryGovernanceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    RowsFetchSpec<MemoryExportRow> fetchSpec;

    @Mock
    Row row;

    private AiMemoryGovernanceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiMemoryGovernanceRepository(databaseClient);
    }

    @Test
    void exportsFilteredRowsInDescendingIdOrder() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        var exported = exportRow();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(exported));

        StepVerifier.create(repository.exportL1(
                        "team-a", "agent-a", "user-1", MemoryType.WORK_FACT, MemoryStatus.ACTIVE,
                        from, to, 5000))
                .expectNext(exported)
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM ai_memory_l1")
                .contains("ORDER BY id DESC")
                .contains("LIMIT :limit")
                .contains("created_at >= :fromAt")
                .contains("created_at <= :toAt");
        verify(spec).bind("teamId", "team-a");
        verify(spec).bind("agentId", "agent-a");
        verify(spec).bind("userId", "user-1");
        verify(spec).bind("memoryType", "WORK_FACT");
        verify(spec).bind("memoryStatus", "ACTIVE");
        verify(spec).bind(eq("fromAt"), any(OffsetDateTime.class));
        verify(spec).bind(eq("toAt"), any(OffsetDateTime.class));
        verify(spec).bind("limit", 5000);
    }

    @Test
    void mapsExportColumns() {
        when(row.get("id", Long.class)).thenReturn(5L);
        when(row.get("record_id", String.class)).thenReturn("record-5");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("scene_name", String.class)).thenReturn("scene-1");
        when(row.get("memory_type", String.class)).thenReturn("WORK_FACT");
        when(row.get("priority", Integer.class)).thenReturn(50);
        when(row.get("status", String.class)).thenReturn("ACTIVE");
        when(row.get("scope", String.class)).thenReturn("PRIVATE");
        when(row.get("version", Integer.class)).thenReturn(2);
        when(row.get("decay_score", BigDecimal.class)).thenReturn(new BigDecimal("95.50"));
        when(row.get("last_accessed_at", Instant.class)).thenReturn(NOW);
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("content", String.class)).thenReturn("内容");

        var mapped = AiMemoryGovernanceRepository.mapExport(row);

        assertThat(mapped.id()).isEqualTo(5L);
        assertThat(mapped.recordId()).isEqualTo("record-5");
        assertThat(mapped.teamId()).isEqualTo("team-a");
        assertThat(mapped.agentId()).isEqualTo("agent-a");
        assertThat(mapped.userId()).isEqualTo("user-1");
        assertThat(mapped.sceneName()).isEqualTo("scene-1");
        assertThat(mapped.memoryType()).isEqualTo("WORK_FACT");
        assertThat(mapped.priority()).isEqualTo(50);
        assertThat(mapped.status()).isEqualTo("ACTIVE");
        assertThat(mapped.scope()).isEqualTo("PRIVATE");
        assertThat(mapped.version()).isEqualTo(2);
        assertThat(mapped.decayScore()).isEqualByComparingTo("95.50");
        assertThat(mapped.lastAccessedAt()).isEqualTo(NOW);
        assertThat(mapped.createdAt()).isEqualTo(NOW);
        assertThat(mapped.content()).isEqualTo("内容");
    }

    private static MemoryExportRow exportRow() {
        return new MemoryExportRow(
                5L,
                "record-5",
                "team-a",
                "agent-a",
                "user-1",
                "scene-1",
                "WORK_FACT",
                50,
                "ACTIVE",
                "PRIVATE",
                1,
                new BigDecimal("95.50"),
                NOW,
                NOW,
                "内容");
    }
}
