package cn.tolim.pudans.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
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

    @Mock
    ReactiveTransactionManager reactiveTransactionManager;

    private AuditStore store;

    @BeforeEach
    void setUp() {
        store = new AuditStore(
                activeRepository, archiveRepository, guardRepository, entityOperations, reactiveTransactionManager);
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
}
