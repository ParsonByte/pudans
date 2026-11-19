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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.extension.Metadata;

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
}
