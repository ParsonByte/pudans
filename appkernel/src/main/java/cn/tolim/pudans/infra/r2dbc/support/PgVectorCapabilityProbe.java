package cn.tolim.pudans.infra.r2dbc.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class PgVectorCapabilityProbe {

    private static final String PROBE_SQL = """
            SELECT
                EXISTS (
                    SELECT 1
                    FROM pg_extension
                    WHERE extname = 'vector'
                ) AS vector_extension_installed,
                EXISTS (
                    SELECT 1
                    FROM pg_type t
                    JOIN pg_extension e ON e.extnamespace = t.typnamespace
                    WHERE e.extname = 'vector' AND t.typname = 'vector'
                ) AS vector_type_available
            """;

    private static final String EXTENSION_INSTALLED_COLUMN = "vector_extension_installed";

    private static final String VECTOR_TYPE_AVAILABLE_COLUMN = "vector_type_available";

    private final DatabaseClient databaseClient;

    public PgVectorCapabilityProbe(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<PgVectorCapability> probe() {
        return databaseClient
                .sql(PROBE_SQL)
                .map((row, metadata) -> PgVectorCapability.from(
                        Boolean.TRUE.equals(row.get(EXTENSION_INSTALLED_COLUMN, Boolean.class)),
                        Boolean.TRUE.equals(row.get(VECTOR_TYPE_AVAILABLE_COLUMN, Boolean.class))))
                .one()
                .defaultIfEmpty(PgVectorCapability.probeFailed())
                .onErrorResume(error -> {
                    log.warn("pgvector 能力探测失败", error);
                    return Mono.just(PgVectorCapability.probeFailed());
                });
    }
}
