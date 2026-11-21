package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.List;
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
class AiOrgMemoryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    RowsFetchSpec<OrgMemoryRecord> fetchSpec;

    @Mock
    AiMemoryGovernanceRepository governanceRepository;

    @Mock
    Row row;

    private AiOrgMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiOrgMemoryRepository(databaseClient, governanceRepository);
    }

    @Test
    void mapsAnOrgMemoryRow() {
        var approvedAt = Instant.parse("2026-11-18T00:00:00Z");
        when(row.get("id", Long.class)).thenReturn(31L);
        when(row.get("content", String.class)).thenReturn("内容");
        when(row.get("content_type", String.class)).thenReturn("WORK_METHOD");
        when(row.get("dedup_hash", String.class)).thenReturn("hash-31");
        when(row.get("trust_level", String.class)).thenReturn("PENDING");
        when(row.get("source_type", String.class)).thenReturn("SINGLE");
        when(row.get("source_team_ids", String[].class))
                .thenReturn(new String[] {"team-a", "team-b"});
        when(row.get("source_user_ids", String[].class)).thenReturn(new String[] {"user-1"});
        when(row.get("source_memory_ids", Long[].class)).thenReturn(new Long[] {11L, 12L});
        when(row.get("cross_vote_count", Integer.class)).thenReturn(2);
        when(row.get("approved_by", String.class)).thenReturn("reviewer");
        when(row.get("approved_at", Instant.class)).thenReturn(approvedAt);
        when(row.get("is_active", Boolean.class)).thenReturn(true);
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("updated_at", Instant.class)).thenReturn(NOW);

        var memory = AiOrgMemoryRepository.mapOrgMemory(row);

        assertThat(memory.id()).isEqualTo(31L);
        assertThat(memory.content()).isEqualTo("内容");
        assertThat(memory.contentType()).isEqualTo("WORK_METHOD");
        assertThat(memory.dedupHash()).isEqualTo("hash-31");
        assertThat(memory.trustLevel()).isEqualTo("PENDING");
        assertThat(memory.sourceType()).isEqualTo("SINGLE");
        assertThat(memory.sourceTeamIds()).containsExactly("team-a", "team-b");
        assertThat(memory.sourceUserIds()).containsExactly("user-1");
        assertThat(memory.sourceMemoryIds()).containsExactly(11L, 12L);
        assertThat(memory.crossVoteCount()).isEqualTo(2);
        assertThat(memory.approvedBy()).isEqualTo("reviewer");
        assertThat(memory.approvedAt()).isEqualTo(approvedAt);
        assertThat(memory.active()).isTrue();
        assertThat(memory.createdAt()).isEqualTo(NOW);
        assertThat(memory.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void mapsMissingLineageArraysToEmptyLists() {
        when(row.get("id", Long.class)).thenReturn(7L);
        when(row.get("content", String.class)).thenReturn("内容");
        when(row.get("content_type", String.class)).thenReturn("WORK_FACT");
        when(row.get("dedup_hash", String.class)).thenReturn("hash-7");
        when(row.get("trust_level", String.class)).thenReturn("PENDING");
        when(row.get("source_type", String.class)).thenReturn("SINGLE");
        when(row.get("cross_vote_count", Integer.class)).thenReturn(1);
        when(row.get("source_team_ids", String[].class)).thenReturn(null);
        when(row.get("source_user_ids", String[].class)).thenReturn(null);
        when(row.get("source_memory_ids", Long[].class)).thenReturn(null);
        when(row.get("approved_by", String.class)).thenReturn(null);
        when(row.get("approved_at", Instant.class)).thenReturn(null);
        when(row.get("is_active", Boolean.class)).thenReturn(null);
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("updated_at", Instant.class)).thenReturn(NOW);

        var memory = AiOrgMemoryRepository.mapOrgMemory(row);

        assertThat(memory.sourceTeamIds()).isEmpty();
        assertThat(memory.sourceUserIds()).isEmpty();
        assertThat(memory.sourceMemoryIds()).isEmpty();
        assertThat(memory.active()).isFalse();
    }

    @Test
    void recordVoteIncrementsVotesAndUnionsLineage() {
        stubSqlExecution();
        when(fetchSpec.one()).thenReturn(Mono.just(orgMemory(31L, "PENDING", true)));

        StepVerifier.create(repository.recordVote(candidate())).expectNextCount(1).verifyComplete();

        var sql = capturedSql();
        assertThat(sql)
                .contains("INSERT INTO ai_org_memory")
                .contains("ON CONFLICT (dedup_hash) DO UPDATE")
                .contains("cross_vote_count = ai_org_memory.cross_vote_count + EXCLUDED.cross_vote_count")
                .contains("source_team_ids = ARRAY(SELECT DISTINCT unnest(")
                .contains("source_user_ids = ARRAY(SELECT DISTINCT unnest(")
                .contains("source_memory_ids = ARRAY(SELECT DISTINCT unnest(")
                .contains("updated_at = now()")
                .doesNotContain("DO NOTHING");
        verify(spec).bind("content", "内容");
        verify(spec).bind("contentType", "WORK_METHOD");
        verify(spec).bind("dedupHash", "hash-1");
        verify(spec).bind("crossVoteCount", 1);
    }

    @Test
    void recordVoteReturnsTheStoredRow() {
        var stored = orgMemory(31L, "PENDING", true);
        stubSqlExecution();
        when(fetchSpec.one()).thenReturn(Mono.just(stored));

        StepVerifier.create(repository.recordVote(candidate()))
                .expectNext(stored)
                .verifyComplete();
    }

    @Test
    void promoteIfEligibleMarksAPendingRowAsVerified() {
        var promoted = orgMemory(31L, "VERIFIED", true);
        stubSqlExecution();
        when(fetchSpec.one()).thenReturn(Mono.just(promoted));

        StepVerifier.create(repository.promoteIfEligible(31L, 3))
                .expectNext(promoted)
                .verifyComplete();

        var sql = capturedSql();
        assertThat(sql)
                .contains("UPDATE ai_org_memory")
                .contains("SET trust_level = 'VERIFIED'")
                .contains("approved_by = 'system'")
                .contains("approved_at = now()")
                .contains("is_active = TRUE")
                .contains("trust_level = 'PENDING'")
                .contains("cross_vote_count >= :threshold");
        verify(spec).bind("id", 31L);
        verify(spec).bind("threshold", 3);
    }

    @Test
    void promoteIfEligibleReturnsEmptyBelowTheThreshold() {
        stubSqlExecution();
        when(fetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.promoteIfEligible(31L, 3)).verifyComplete();
    }

    @Test
    void listRevokedReadsInactiveRowsWithCursorAndLimit() {
        var revoked = orgMemory(9L, "REVOKED", false);
        stubSqlExecution();
        when(fetchSpec.all()).thenReturn(Flux.just(revoked));

        StepVerifier.create(repository.listRevoked(50L, 20))
                .expectNext(revoked)
                .verifyComplete();

        var sql = capturedSql();
        assertThat(sql)
                .contains("FROM ai_org_memory")
                .contains("trust_level = :trustLevel")
                .doesNotContain("is_active = TRUE");
        verify(spec).bind("trustLevel", "REVOKED");
        verify(spec).bind("beforeId", 50L);
        verify(spec).bind("limit", 20);
    }

    @Test
    void listPendingDelegatesToTheGovernanceRepository() {
        var pending = orgMemory(5L, "PENDING", true);
        when(governanceRepository.listOrgMemories("PENDING", 50L, 20)).thenReturn(Flux.just(pending));

        StepVerifier.create(repository.listPending(50L, 20))
                .expectNext(pending)
                .verifyComplete();
    }

    @Test
    void listApprovedDelegatesToTheGovernanceRepository() {
        var verified = orgMemory(6L, "VERIFIED", true);
        when(governanceRepository.listOrgMemories("VERIFIED", 50L, 20)).thenReturn(Flux.just(verified));

        StepVerifier.create(repository.listApproved(50L, 20))
                .expectNext(verified)
                .verifyComplete();
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

    private static AiOrgMemoryRepository.NewOrgMemory candidate() {
        return new AiOrgMemoryRepository.NewOrgMemory(
                "内容",
                "WORK_METHOD",
                "hash-1",
                List.of("team-a"),
                List.of("user-1"),
                List.of(11L),
                1);
    }

    private static OrgMemoryRecord orgMemory(long id, String trustLevel, boolean active) {
        return new OrgMemoryRecord(
                id,
                "内容",
                "WORK_METHOD",
                "hash-" + id,
                trustLevel,
                "SINGLE",
                List.of("team-a"),
                List.of("user-1"),
                List.of(11L),
                1,
                null,
                null,
                active,
                NOW,
                NOW);
    }
}
