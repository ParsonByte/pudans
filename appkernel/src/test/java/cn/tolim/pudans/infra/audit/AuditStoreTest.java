package cn.tolim.pudans.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.extension.contract.Metadata;

@ExtendWith(MockitoExtension.class)
class AuditStoreTest {

    @Mock
    AuditRecordRepository activeRepository;

    @Mock
    AuditArchiveRepository archiveRepository;

    @Mock
    AuditArchiveGuardRepository guardRepository;

    @Mock
    R2dbcEntityOperations entityOperations;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    DatabaseClient databaseClient;

    @Mock
    ReactiveTransactionManager reactiveTransactionManager;

    @Mock
    RowsFetchSpec<Map.Entry<String, Long>> resultRows;

    @Mock
    RowsFetchSpec<Map.Entry<LocalDate, Long>> dayRows;

    @Mock
    RowsFetchSpec<AuditStore.LatencyStats> latencyRows;

    @Mock
    RowsFetchSpec<AuditStore.HeatmapBucket> heatmapRows;

    @Mock
    RowsFetchSpec<AuditStore.ActorStat> actorRows;

    @Mock
    RowsFetchSpec<AuditStore.FlowStats> flowStatsRows;

    @Mock
    RowsFetchSpec<AuditStore.FlowDayBucket> flowDayRows;

    private AuditStore store;

    @BeforeEach
    void setUp() {
        store = new AuditStore(
                activeRepository,
                archiveRepository,
                guardRepository,
                entityOperations,
                databaseClient,
                reactiveTransactionManager);
    }

    private static AuditRecordEntity entity(String id) {
        var entity = new AuditRecordEntity();
        entity.setId(id);
        return entity;
    }

    private static AuditArchiveEntity archiveEntity(String id) {
        var entity = new AuditArchiveEntity();
        entity.setId(id);
        return entity;
    }

    private static AuditRecordEntity entity(String id, String traceFlowId, Instant occurredAt) {
        var entity = entity(id);
        entity.setTraceFlowId(traceFlowId);
        entity.setOccurredAt(OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
        return entity;
    }

    private void givenStores(
            Flux<AuditRecordEntity> active, Flux<AuditArchiveEntity> archived) {
        doReturn(active).when(entityOperations).select(any(Query.class), eq(AuditRecordEntity.class));
        doReturn(archived).when(entityOperations).select(any(Query.class), eq(AuditArchiveEntity.class));
    }

    private List<String> queryNames(AuditStore.AuditQuery query) {
        return store.query(query).map(value -> value.getMetadata().getName()).collectList().block();
    }

    @Test
    void queryDefaultsToDescendingOrderAcrossActiveAndArchivedStores() {
        givenStores(Flux.just(entity("0001"), entity("0003")), Flux.just(archiveEntity("0002"), archiveEntity("0004")));

        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, false);

        assertThat(queryNames(query)).containsExactly("0004", "0003", "0002", "0001");
    }

    @Test
    void queryKeepsAscendingOrderWhenRequested() {
        givenStores(Flux.just(entity("0001"), entity("0003")), Flux.just(archiveEntity("0002")));

        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, true);

        assertThat(queryNames(query)).containsExactly("0001", "0002", "0003");
    }

    @Test
    void pageReturnsTheRequestedSliceAndTheTotalAcrossStores() {
        givenStores(
                Flux.just(entity("0001"), entity("0003"), entity("0005")),
                Flux.just(archiveEntity("0002"), archiveEntity("0004")));

        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, false);
        var result = store.page(query, 2, 2).block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.items())
                .extracting(record -> record.getMetadata().getName())
                .containsExactly("0003", "0002");
    }

    @Test
    void pageCapsTheFetchedRowCountForVeryLargePageNumbers() {
        givenStores(Flux.empty(), Flux.empty());
        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, false);

        var result = store.page(query, Integer.MAX_VALUE, 100).block();

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
        var captor = ArgumentCaptor.forClass(Query.class);
        verify(entityOperations, times(4)).select(captor.capture(), any(Class.class));
        assertThat(captor.getAllValues())
                .extracting(Query::getLimit)
                .allMatch(limit -> limit <= AuditStore.MAX_PAGE_RECORDS);
    }

    @Test
    void traceFlowsGroupsRecordsByFlowAndPaginates() {
        var start = Instant.parse("2026-11-18T10:00:00Z");
        givenStores(
                Flux.just(
                        entity("0001", "flow-a", start.plusMillis(100)),
                        entity("0002", "flow-a", start.plusMillis(200)),
                        entity("0003", "flow-b", start.plusMillis(300))),
                Flux.empty());

        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, false);
        var first = store.traceFlows(query, 1, 1).block();
        var second = store.traceFlows(query, 2, 1).block();

        assertThat(first).isNotNull();
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.traceFlowIds()).containsExactly("flow-b");
        assertThat(second).isNotNull();
        assertThat(second.traceFlowIds()).containsExactly("flow-a");
    }

    @Test
    void findRecentReturnsTheNewestRecordsFirst() {
        givenStores(Flux.just(entity("0001"), entity("0003")), Flux.just(archiveEntity("0005")));

        List<String> ids = store
                .findRecent(2)
                .map(record -> record.getMetadata().getName())
                .collectList()
                .block();
        assertThat(ids).containsExactly("0005", "0003");
    }

    @Test
    void countSinceSumsActiveAndArchivedRecords() {
        givenStores(Flux.just(entity("0001"), entity("0003")), Flux.just(archiveEntity("0002")));

        Long count = store.countSince(Instant.parse("2026-11-15T00:00:00Z")).block();
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void countByResultAggregatesBothAuditStores() {
        var spec = givenResultQuery();

        store.countByResult(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"),
                        "LOGIN")
                .block();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM (", "UNION ALL", ") grouped GROUP BY result")
                .contains("AND operation = :operation")
                .doesNotContain("resultUNION");
        verify(spec, times(3)).bind(anyString(), any());
    }

    @Test
    void countPerDayUsesTheSameStaticRequestFilterAsTheAuditTable() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, Map.Entry<LocalDate, Long>>>any()))
                .thenReturn(dayRows);
        when(dayRows.all())
                .thenReturn(Flux.just(Map.entry(LocalDate.parse("2026-11-23"), 3L)));

        var counts = store
                .countPerDay(Instant.parse("2026-11-15T00:00:00Z"), null, null, null)
                .block();

        assertThat(counts).containsExactly(Map.entry(LocalDate.parse("2026-11-23"), 3L));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "FROM audit_record",
                        "FROM audit_record_archive",
                        "request_path <> '/login-bg.mp4'");
        verify(spec).bind(anyString(), any());
    }

    @Test
    void countPerDayAppliesTheExclusiveEndWhenProvided() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, Map.Entry<LocalDate, Long>>>any()))
                .thenReturn(dayRows);
        when(dayRows.all()).thenReturn(Flux.empty());

        store.countPerDay(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"),
                        null,
                        null,
                        "SUCCESS")
                .block();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("occurred_at >= :since", "occurred_at < :to", "request_path <> '/login-bg.mp4'");
        verify(spec, times(3)).bind(anyString(), any());
    }

    @Test
    void topOperationsAggregatesBothAuditStores() {
        givenResultQuery();

        store.topOperations(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"),
                        6)
                .block();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM (", "UNION ALL", ") grouped GROUP BY operation")
                .doesNotContain("operation = :operation", "operationUNION");
    }

    @Test
    void latencyStatsAggregatesBothAuditStores() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.LatencyStats>>any()))
                .thenReturn(latencyRows);
        when(latencyRows.one()).thenReturn(Mono.just(new AuditStore.LatencyStats(42L, 95L)));

        var stats = store.latencyStats(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .block();

        assertThat(stats).isEqualTo(new AuditStore.LatencyStats(42L, 95L));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "WITH records AS (",
                        "FROM audit_record",
                        "UNION ALL",
                        "FROM audit_record_archive",
                        "trace_flow_id",
                        "FALSE AS archived",
                        "TRUE AS archived",
                        "PERCENTILE_CONT(0.95)",
                        "request_path NOT LIKE '/assets/%'");
        verify(spec, times(2)).bind(anyString(), any());
    }

    @Test
    void activityHeatmapAggregatesBeijingWeekdayAndHourBuckets() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.HeatmapBucket>>any()))
                .thenReturn(heatmapRows);
        when(heatmapRows.all())
                .thenReturn(Flux.just(new AuditStore.HeatmapBucket(3, 11, 4)));

        var buckets = store.activityHeatmap(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .collectList()
                .block();

        assertThat(buckets).containsExactly(new AuditStore.HeatmapBucket(3, 11, 4));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "EXTRACT(ISODOW FROM occurred_at AT TIME ZONE 'Asia/Shanghai')",
                        "EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Shanghai')",
                        "FROM audit_record_archive");
        verify(spec, times(2)).bind(anyString(), any());
    }

    @Test
    void durationBucketsUseTheFixedAggregationWindows() {
        givenResultQuery(Flux.just(
                Map.entry("over3s", 2L),
                Map.entry("under50", 3L)));

        var buckets = store.durationBuckets(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .block();

        assertThat(buckets)
                .containsEntry("under50", 3L)
                .containsEntry("over3s", 2L)
                .hasSize(2);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "duration_ms < 50 THEN 'under50'",
                        "duration_ms < 200 THEN '50to199'",
                        "duration_ms < 500 THEN '200to499'",
                        "duration_ms < 1000 THEN '500to999'",
                        "duration_ms < 3000 THEN '1to3s'",
                        "ELSE 'over3s'",
                        "FROM audit_record_archive");
    }

    @Test
    void topActorsAggregatesBothAuditStoresAndExcludesAnonymousUsers() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.ActorStat>>any()))
                .thenReturn(actorRows);
        when(actorRows.all())
                .thenReturn(Flux.just(new AuditStore.ActorStat("uid-1", 4, 2, 2, 35)));

        var actors = store.topActors(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"),
                        6)
                .collectList()
                .block();

        assertThat(actors).containsExactly(new AuditStore.ActorStat("uid-1", 4, 2, 2, 35));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "COUNT(*) FILTER (WHERE result <> 'SUCCESS') AS failures",
                        "COUNT(DISTINCT operation) AS operations",
                        "LOWER(uid) NOT IN ('anonymous', 'unknown')",
                        "LIMIT :limit",
                        "FROM audit_record_archive");
        verify(spec, times(3)).bind(anyString(), any());
    }

    @Test
    void flowStatsAggregateStartEndStatusArchivedAndDuration() {
        var spec = givenDatabaseQuery();
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.FlowStats>>any()))
                .thenReturn(flowStatsRows);
        when(flowStatsRows.one())
                .thenReturn(Mono.just(new AuditStore.FlowStats(12L, 4L, 2L, 1L, 3.0, 1200L, 3000L)));

        var stats = store.flowStats(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .block();

        assertThat(stats)
                .isEqualTo(new AuditStore.FlowStats(12L, 4L, 2L, 1L, 3.0, 1200L, 3000L));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "MIN(occurred_at - GREATEST(duration_ms, 0) * INTERVAL '1 millisecond')",
                        "MAX(occurred_at) AS ended_at",
                        "COUNT(*) FILTER (WHERE archived) AS archived_step_count",
                        "BOOL_OR(result IN ('DENIED', 'FAILURE', 'FAILED')) AS failed",
                        "BOOL_OR(archived) AS archived",
                        "AVG(step_count)",
                        "PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)",
                        "BTRIM(trace_flow_id) <> ''");
        verify(spec, times(2)).bind(anyString(), any());
    }

    @Test
    void dailyFlowBucketsAggregateByFlowStartInBeijing() {
        var spec = givenDatabaseQuery();
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.FlowDayBucket>>any()))
                .thenReturn(flowDayRows);
        when(flowDayRows.all())
                .thenReturn(Flux.just(new AuditStore.FlowDayBucket(
                        LocalDate.parse("2026-11-18"), 2L, 5L, 1L, 2L)));

        var buckets = store.dailyFlowBuckets(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .collectList()
                .block();

        assertThat(buckets).containsExactly(new AuditStore.FlowDayBucket(
                LocalDate.parse("2026-11-18"), 2L, 5L, 1L, 2L));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "started_at AT TIME ZONE 'Asia/Shanghai'",
                        "COUNT(*) AS flows",
                        "SUM(step_count) AS records",
                        "COUNT(*) FILTER (WHERE failed) AS failed_flows",
                        "SUM(archived_step_count) AS archived_records",
                        "GROUP BY 1");
        verify(spec, times(2)).bind(anyString(), any());
    }

    @Test
    void flowHeatmapBucketsUseTheAggregatedFlowStart() {
        var spec = givenDatabaseQuery();
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, AuditStore.HeatmapBucket>>any()))
                .thenReturn(heatmapRows);
        when(heatmapRows.all()).thenReturn(Flux.just(new AuditStore.HeatmapBucket(3, 11, 2)));

        var buckets = store.flowHeatmap(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .collectList()
                .block();

        assertThat(buckets).containsExactly(new AuditStore.HeatmapBucket(3, 11, 2));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "EXTRACT(ISODOW FROM started_at AT TIME ZONE 'Asia/Shanghai')",
                        "EXTRACT(HOUR FROM started_at AT TIME ZONE 'Asia/Shanghai')",
                        "COUNT(*) AS total",
                        "FROM flows");
        verify(spec, times(2)).bind(anyString(), any());
    }

    @Test
    void flowStepBucketsUseTheFixedStepWindows() {
        givenResultQuery(Flux.just(
                Map.entry("4to5", 2L),
                Map.entry("over10", 1L)));

        var buckets = store.flowStepBuckets(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .block();

        assertThat(buckets)
                .containsEntry("4to5", 2L)
                .containsEntry("over10", 1L)
                .hasSize(2);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "step_count = 1 THEN '1'",
                        "step_count = 2 THEN '2'",
                        "step_count = 3 THEN '3'",
                        "step_count <= 5 THEN '4to5'",
                        "step_count <= 10 THEN '6to10'",
                        "ELSE 'over10'",
                        "FROM flows");
    }

    @Test
    void flowDurationBucketsUseTheExistingDurationWindows() {
        givenResultQuery(Flux.just(
                Map.entry("over3s", 3L),
                Map.entry("under50", 1L)));

        var buckets = store.flowDurationBuckets(
                        Instant.parse("2026-11-15T00:00:00Z"),
                        Instant.parse("2026-11-22T00:00:00Z"))
                .block();

        assertThat(buckets)
                .containsEntry("under50", 1L)
                .containsEntry("over3s", 3L)
                .hasSize(2);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains(
                        "duration_ms < 50 THEN 'under50'",
                        "duration_ms < 200 THEN '50to199'",
                        "duration_ms < 500 THEN '200to499'",
                        "duration_ms < 1000 THEN '500to999'",
                        "duration_ms < 3000 THEN '1to3s'",
                        "ELSE 'over3s'",
                        "FROM flow_durations");
    }

    @Test
    void archiveStatsReturnsActiveAndArchivedCounts() {
        givenStores(
                Flux.just(entity("0001"), entity("0002"), entity("0003")),
                Flux.just(
                        archiveEntity("0004"),
                        archiveEntity("0005"),
                        archiveEntity("0006"),
                        archiveEntity("0007"),
                        archiveEntity("0008")));

        var stats = store.archiveStats().block();

        assertThat(stats).isNotNull();
        assertThat(stats.activeCount()).isEqualTo(3L);
        assertThat(stats.archivedCount()).isEqualTo(5L);
    }

    @Test
    void archivePageReadsArchivedRecordsAndReturnsTotal() {
        doReturn(Flux.just(archiveEntity("0002"), archiveEntity("0001")))
                .when(entityOperations)
                .select(any(Query.class), eq(AuditArchiveEntity.class));
        var query = new AuditStore.AuditQuery(null, null, null, null, null, null, null, null, 10, false);

        var result = store.archivePage(query, 1, 10).block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.items())
                .extracting(record -> record.getMetadata().getName())
                .containsExactly("0002", "0001");
    }

    @Test
    void appendSkipsAnAuditRecordWhoseFingerprintAlreadyExists() {
        when(activeRepository.existsByFingerprint(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(store.append(record())).verifyComplete();

        verify(activeRepository).existsByFingerprint(anyString());
        verifyNoInteractions(entityOperations);
    }

    @Test
    void appendTreatsAConcurrentDuplicateAsAlreadyRecorded() {
        when(activeRepository.existsByFingerprint(anyString())).thenReturn(Mono.just(false));
        when(archiveRepository.existsByFingerprint(anyString())).thenReturn(Mono.just(false));
        when(entityOperations.insert(any(AuditRecordEntity.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate fingerprint")));

        StepVerifier.create(store.append(record())).verifyComplete();
    }

    @Test
    void archiveSkipsTheBatchWhenAnotherInstanceHoldsTheGuard() {
        when(databaseClient.sql(anyString()).fetch().rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(store.archiveBefore(Instant.now()))
                .expectNext(0L)
                .verifyComplete();

        verify(guardRepository, never()).save(any());
        verifyNoInteractions(activeRepository, archiveRepository);
    }

    @Test
    void archiveReleasesTheGuardAfterTheLastBatch() {
        when(databaseClient.sql(anyString()).fetch().rowsUpdated()).thenReturn(Mono.just(1L));
        when(guardRepository.save(any(AuditArchiveGuardEntity.class)))
                .thenReturn(Mono.just(new AuditArchiveGuardEntity(1, false)));
        doReturn(Flux.empty()).when(entityOperations).select(any(Query.class), eq(AuditRecordEntity.class));

        StepVerifier.create(store.archiveBefore(Instant.now()))
                .expectNext(0L)
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(AuditArchiveGuardEntity.class);
        verify(guardRepository).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    private static AuditRecord record() {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName("audit-1");
        record.setMetadata(metadata);
        record.setOperation("READ");
        record.setResourceType("User");
        record.setResourceName("alice");
        record.setRequestMethod("GET");
        record.setRequestPath("/apis/users/alice");
        record.setResult("SUCCESS");
        return record;
    }

    private DatabaseClient.GenericExecuteSpec givenResultQuery() {
        return givenResultQuery(Flux.empty());
    }

    private DatabaseClient.GenericExecuteSpec givenDatabaseQuery() {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        return spec;
    }

    private DatabaseClient.GenericExecuteSpec givenResultQuery(
            Flux<Map.Entry<String, Long>> rows) {
        var spec = org.mockito.Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(ArgumentMatchers.<BiFunction<Row, RowMetadata, Map.Entry<String, Long>>>any()))
                .thenReturn(resultRows);
        when(resultRows.all()).thenReturn(rows);
        return spec;
    }
}
