package cn.tolim.pudans.infra.r2dbc.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PgVectorCapabilityProbeTest {

    private static final String EXTENSION_INSTALLED_COLUMN = "vector_extension_installed";

    private static final String VECTOR_TYPE_AVAILABLE_COLUMN = "vector_type_available";

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;

    @Mock
    private RowsFetchSpec<PgVectorCapability> rows;

    private PgVectorCapabilityProbe probe;

    @BeforeEach
    void setUp() {
        probe = new PgVectorCapabilityProbe(databaseClient);
        when(databaseClient.sql(anyString())).thenReturn(executeSpec);
    }

    @Test
    void reportsUsableWhenVectorExtensionAndTypeExist() {
        givenCatalogRow(true, true);

        StepVerifier.create(probe.probe())
                .expectNext(new PgVectorCapability(true, true, true, "memory-vector-ready"))
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM pg_extension", "FROM pg_type", "JOIN pg_extension")
                .doesNotContainIgnoringCase("CREATE EXTENSION", "CREATE TABLE", "ALTER TABLE");
    }

    @Test
    void reportsMissingExtensionAsUnavailable() {
        givenCatalogRow(false, false);

        StepVerifier.create(probe.probe())
                .expectNext(new PgVectorCapability(
                        false, false, false, "memory-vector-extension-missing"))
                .verifyComplete();
    }

    @Test
    void reportsMissingVectorTypeAsUnavailable() {
        givenCatalogRow(true, false);

        StepVerifier.create(probe.probe())
                .expectNext(new PgVectorCapability(true, false, false, "memory-vector-type-missing"))
                .verifyComplete();
    }

    @Test
    void mapsQueryFailureToUnavailableStatus() {
        when(executeSpec.map(ArgumentMatchers
                        .<BiFunction<Row, RowMetadata, PgVectorCapability>>any()))
                .thenReturn(rows);
        when(rows.one()).thenReturn(Mono.error(new IllegalStateException("catalog unavailable")));

        StepVerifier.create(probe.probe())
                .expectNext(new PgVectorCapability(false, false, false, "memory-vector-probe-failed"))
                .verifyComplete();
    }

    private void givenCatalogRow(boolean extensionInstalled, boolean vectorTypeAvailable) {
        var row = org.mockito.Mockito.mock(Row.class);
        var metadata = org.mockito.Mockito.mock(RowMetadata.class);
        when(row.get(EXTENSION_INSTALLED_COLUMN, Boolean.class)).thenReturn(extensionInstalled);
        when(row.get(VECTOR_TYPE_AVAILABLE_COLUMN, Boolean.class)).thenReturn(vectorTypeAvailable);
        var mapper = new AtomicReference<BiFunction<Row, RowMetadata, PgVectorCapability>>();
        when(executeSpec.map(ArgumentMatchers
                        .<BiFunction<Row, RowMetadata, PgVectorCapability>>any()))
                .thenAnswer(invocation -> {
                    mapper.set(invocation.getArgument(0));
                    return rows;
                });
        when(rows.one()).thenReturn(Mono.defer(
                () -> Mono.just(mapper.get().apply(row, metadata))));
    }
}
