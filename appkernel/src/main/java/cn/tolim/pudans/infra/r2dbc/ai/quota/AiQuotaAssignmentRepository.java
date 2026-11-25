package cn.tolim.pudans.infra.r2dbc.ai.quota;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiQuotaAssignment;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiQuotaAssignmentRepository {

    private static final String COLUMNS = """
            SELECT id, subject_type, subject_id, plan_id, effective_from, effective_to,
                   weekly_anchor_at, status, created_at, updated_at
              FROM ai_quota_assignments
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiQuotaAssignment> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY subject_type, subject_id, effective_from DESC")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiQuotaAssignment> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiQuotaAssignment> findActive(
            String subjectType, String subjectId, Instant at) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE subject_type = :subjectType
                           AND subject_id = :subjectId
                           AND status = 'active'
                           AND effective_from <= :at
                           AND (effective_to IS NULL OR effective_to > :at)
                         ORDER BY effective_from DESC, created_at DESC
                         LIMIT 1
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("at", utc(at))
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<AiQuotaAssignment> findActiveRoles(Collection<String> roleNames, Instant at) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Flux.empty();
        }
        var placeholders = new StringBuilder();
        var index = 0;
        for (var ignored : roleNames) {
            if (!placeholders.isEmpty()) {
                placeholders.append(", ");
            }
            placeholders.append(":role").append(index++);
        }
        var spec = databaseClient.sql(COLUMNS + """
                         WHERE subject_type = 'role'
                           AND subject_id IN (%s)
                           AND status = 'active'
                           AND effective_from <= :at
                           AND (effective_to IS NULL OR effective_to > :at)
                         ORDER BY effective_from DESC, created_at DESC
                        """.formatted(placeholders))
                .bind("at", utc(at));
        index = 0;
        for (var roleName : roleNames) {
            spec = spec.bind("role" + index++, roleName);
        }
        return spec.map((row, metadata) -> map(row)).all();
    }

    public Mono<AiQuotaAssignment> insert(AiQuotaAssignment assignment) {
        var spec = baseSpec(databaseClient.sql("""
                INSERT INTO ai_quota_assignments (
                    id, subject_type, subject_id, plan_id, effective_from, effective_to,
                    weekly_anchor_at, status, created_at, updated_at)
                VALUES (
                    :id, :subjectType, :subjectId, :planId, :effectiveFrom, :effectiveTo,
                    :weeklyAnchorAt, :status, :createdAt, :updatedAt)
                """), assignment);
        return duplicateAs(
                spec.bind("createdAt", utc(assignment.createdAt()))
                        .bind("updatedAt", utc(assignment.updatedAt()))
                        .then()
                        .thenReturn(assignment),
                "ai-quota-assignment-conflict",
                "同一主体的配额分配已存在。");
    }

    public Mono<AiQuotaAssignment> update(AiQuotaAssignment assignment) {
        var spec = baseSpec(databaseClient.sql("""
                UPDATE ai_quota_assignments
                   SET subject_type = :subjectType,
                       subject_id = :subjectId,
                       plan_id = :planId,
                       effective_from = :effectiveFrom,
                       effective_to = :effectiveTo,
                       weekly_anchor_at = :weeklyAnchorAt,
                       status = :status,
                       updated_at = :updatedAt
                 WHERE id = :id
                """), assignment);
        return duplicateAs(
                spec.bind("updatedAt", utc(assignment.updatedAt()))
                        .then()
                        .thenReturn(assignment),
                "ai-quota-assignment-conflict",
                "同一主体的配额分配已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient
                .sql("DELETE FROM ai_quota_assignments WHERE id = :id")
                .bind("id", id)
                .then();
    }

    public Mono<Boolean> existsByPlanId(UUID planId) {
        return databaseClient
                .sql("SELECT EXISTS (SELECT 1 FROM ai_quota_assignments WHERE plan_id = :id)")
                .bind("id", planId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static DatabaseClient.GenericExecuteSpec baseSpec(
            DatabaseClient.GenericExecuteSpec spec, AiQuotaAssignment assignment) {
        spec = spec.bind("id", assignment.id())
                .bind("subjectType", assignment.subjectType())
                .bind("subjectId", assignment.subjectId())
                .bind("planId", assignment.planId())
                .bind("effectiveFrom", utc(assignment.effectiveFrom()));
        spec = bindNullable(spec, "effectiveTo", utc(assignment.effectiveTo()), OffsetDateTime.class);
        return spec.bind("weeklyAnchorAt", utc(assignment.weeklyAnchorAt()))
                .bind("status", assignment.status());
    }

    private static AiQuotaAssignment map(io.r2dbc.spi.Row row) {
        return new AiQuotaAssignment(
                row.get("id", UUID.class),
                row.get("subject_type", String.class),
                row.get("subject_id", String.class),
                row.get("plan_id", UUID.class),
                instant(row.get("effective_from", OffsetDateTime.class)),
                instant(row.get("effective_to", OffsetDateTime.class)),
                instant(row.get("weekly_anchor_at", OffsetDateTime.class)),
                row.get("status", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
