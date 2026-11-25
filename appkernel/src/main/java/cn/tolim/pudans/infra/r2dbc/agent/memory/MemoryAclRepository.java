package cn.tolim.pudans.infra.r2dbc.agent.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryAclRepository {

    private final DatabaseClient databaseClient;

    public Mono<Boolean> exists(String subjectType, String subjectId, String permission) {
        return databaseClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM memory_acl
                             WHERE memory_layer = 'L4'
                               AND subject_type = :subjectType
                               AND subject_id = :subjectId
                               AND permission = :permission)
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("permission", permission)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }
}
