package cn.tolim.pudans.infra.r2dbc.ai.quota;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiQuotaReservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiQuotaReservationRepository {

    private static final String COLUMNS = """
            SELECT id, subject_type, subject_id, plan_id, run_id, task_id,
                   reserved_credits, actual_credits, reserved_tool_calls, actual_tool_calls,
                   reserved_mcp_calls, actual_mcp_calls, five_hour_bucket_at,
                   weekly_period_start, status, idempotency_key, expires_at, created_at,
                   settled_at
              FROM ai_quota_reservations
            """;

    private final DatabaseClient databaseClient;

    public Mono<AiQuotaReservation> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiQuotaReservation> findByIdempotencyKey(String idempotencyKey) {
        return databaseClient
                .sql(COLUMNS + " WHERE idempotency_key = :idempotencyKey")
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiQuotaReservation> insert(AiQuotaReservation reservation) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO ai_quota_reservations (
                            id, subject_type, subject_id, plan_id, run_id, task_id,
                            reserved_credits, actual_credits, reserved_tool_calls,
                            actual_tool_calls, reserved_mcp_calls, actual_mcp_calls,
                            five_hour_bucket_at, weekly_period_start, status,
                            idempotency_key, expires_at, created_at, settled_at)
                        VALUES (
                            :id, :subjectType, :subjectId, :planId, :runId, :taskId,
                            :reservedCredits, :actualCredits, :reservedToolCalls,
                            :actualToolCalls, :reservedMcpCalls, :actualMcpCalls,
                            :fiveHourBucketAt, :weeklyPeriodStart, :status,
                            :idempotencyKey, :expiresAt, :createdAt, :settledAt)
                        """)
                .bind("id", reservation.id())
                .bind("subjectType", reservation.subjectType())
                .bind("subjectId", reservation.subjectId())
                .bind("planId", reservation.planId());
        spec = bindNullable(spec, "runId", reservation.runId(), UUID.class);
        spec = bindNullable(spec, "taskId", reservation.taskId(), UUID.class);
        spec = spec.bind("reservedCredits", reservation.reservedCredits())
                .bind("actualCredits", reservation.actualCredits())
                .bind("reservedToolCalls", reservation.reservedToolCalls())
                .bind("actualToolCalls", reservation.actualToolCalls())
                .bind("reservedMcpCalls", reservation.reservedMcpCalls())
                .bind("actualMcpCalls", reservation.actualMcpCalls())
                .bind("fiveHourBucketAt", utc(reservation.fiveHourBucketAt()))
                .bind("weeklyPeriodStart", utc(reservation.weeklyPeriodStart()))
                .bind("status", reservation.status())
                .bind("idempotencyKey", reservation.idempotencyKey())
                .bind("expiresAt", utc(reservation.expiresAt()))
                .bind("createdAt", utc(reservation.createdAt()));
        spec = bindNullable(spec, "settledAt", utc(reservation.settledAt()), OffsetDateTime.class);
        return duplicateAs(
                spec.then().thenReturn(reservation),
                "ai-quota-reservation-conflict",
                "配额预扣记录已存在。");
    }

    public Mono<AiQuotaReservation> settle(
            UUID id,
            BigDecimal actualCredits,
            int actualToolCalls,
            int actualMcpCalls,
            Instant settledAt) {
        return databaseClient
                .sql("""
                        UPDATE ai_quota_reservations
                           SET status = 'settled',
                               actual_credits = :actualCredits,
                               actual_tool_calls = :actualToolCalls,
                               actual_mcp_calls = :actualMcpCalls,
                               settled_at = :settledAt
                         WHERE id = :id
                           AND status = 'reserved'
                        """)
                .bind("id", id)
                .bind("actualCredits", actualCredits)
                .bind("actualToolCalls", actualToolCalls)
                .bind("actualMcpCalls", actualMcpCalls)
                .bind("settledAt", utc(settledAt))
                .then()
                .then(findById(id));
    }

    public Mono<AiQuotaReservation> release(UUID id) {
        return databaseClient
                .sql("""
                        UPDATE ai_quota_reservations
                           SET status = 'released',
                               actual_credits = 0,
                               actual_tool_calls = 0,
                               actual_mcp_calls = 0
                         WHERE id = :id
                           AND status = 'reserved'
                        """)
                .bind("id", id)
                .then()
                .then(findById(id));
    }

    public Mono<Boolean> existsByPlanId(UUID planId) {
        return databaseClient
                .sql("SELECT EXISTS (SELECT 1 FROM ai_quota_reservations WHERE plan_id = :id)")
                .bind("id", planId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<AiQuotaUsage> fiveHourUsage(String subjectType, String subjectId, Instant after) {
        return databaseClient
                .sql("""
                        SELECT COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_credits
                                                 ELSE reserved_credits END), 0) AS credits,
                               COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_tool_calls
                                                 ELSE reserved_tool_calls END), 0)::int AS tool_calls,
                               COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_mcp_calls
                                                 ELSE reserved_mcp_calls END), 0)::int AS mcp_calls,
                               MIN(five_hour_bucket_at) AS oldest_event_at
                          FROM ai_quota_reservations
                         WHERE subject_type = :subjectType
                           AND subject_id = :subjectId
                           AND status IN ('reserved', 'settled')
                           AND five_hour_bucket_at > :after
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("after", utc(after))
                .map((row, metadata) -> usage(row))
                .one()
                .defaultIfEmpty(AiQuotaUsage.empty());
    }

    public Mono<AiQuotaUsage> weeklyUsage(
            String subjectType, String subjectId, Instant weeklyPeriodStart) {
        return databaseClient
                .sql("""
                        SELECT COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_credits
                                                 ELSE reserved_credits END), 0) AS credits,
                               COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_tool_calls
                                                 ELSE reserved_tool_calls END), 0)::int AS tool_calls,
                               COALESCE(SUM(CASE WHEN status = 'settled'
                                                 THEN actual_mcp_calls
                                                 ELSE reserved_mcp_calls END), 0)::int AS mcp_calls,
                               MIN(five_hour_bucket_at) AS oldest_event_at
                          FROM ai_quota_reservations
                         WHERE subject_type = :subjectType
                           AND subject_id = :subjectId
                           AND status IN ('reserved', 'settled')
                           AND weekly_period_start = :weeklyPeriodStart
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("weeklyPeriodStart", utc(weeklyPeriodStart))
                .map((row, metadata) -> usage(row))
                .one()
                .defaultIfEmpty(AiQuotaUsage.empty());
    }

    public Mono<AiQuotaUsage> settledFiveHourUsage(
            String subjectType, String subjectId, Instant after) {
        return databaseClient
                .sql("""
                        SELECT COALESCE(SUM(actual_credits), 0) AS credits,
                               COALESCE(SUM(actual_tool_calls), 0)::int AS tool_calls,
                               COALESCE(SUM(actual_mcp_calls), 0)::int AS mcp_calls,
                               MIN(five_hour_bucket_at) AS oldest_event_at
                          FROM ai_quota_reservations
                         WHERE subject_type = :subjectType
                           AND subject_id = :subjectId
                           AND status = 'settled'
                           AND five_hour_bucket_at > :after
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("after", utc(after))
                .map((row, metadata) -> usage(row))
                .one()
                .defaultIfEmpty(AiQuotaUsage.empty());
    }

    public Mono<AiQuotaUsage> settledWeeklyUsage(
            String subjectType, String subjectId, Instant weeklyPeriodStart) {
        return databaseClient
                .sql("""
                        SELECT COALESCE(SUM(actual_credits), 0) AS credits,
                               COALESCE(SUM(actual_tool_calls), 0)::int AS tool_calls,
                               COALESCE(SUM(actual_mcp_calls), 0)::int AS mcp_calls,
                               MIN(five_hour_bucket_at) AS oldest_event_at
                          FROM ai_quota_reservations
                         WHERE subject_type = :subjectType
                           AND subject_id = :subjectId
                           AND status = 'settled'
                           AND weekly_period_start = :weeklyPeriodStart
                        """)
                .bind("subjectType", subjectType)
                .bind("subjectId", subjectId)
                .bind("weeklyPeriodStart", utc(weeklyPeriodStart))
                .map((row, metadata) -> usage(row))
                .one()
                .defaultIfEmpty(AiQuotaUsage.empty());
    }

    private static AiQuotaUsage usage(io.r2dbc.spi.Row row) {
        return new AiQuotaUsage(
                row.get("credits", BigDecimal.class),
                row.get("tool_calls", Integer.class),
                row.get("mcp_calls", Integer.class),
                instant(row.get("oldest_event_at", OffsetDateTime.class)));
    }

    private static AiQuotaReservation map(io.r2dbc.spi.Row row) {
        return new AiQuotaReservation(
                row.get("id", UUID.class),
                row.get("subject_type", String.class),
                row.get("subject_id", String.class),
                row.get("plan_id", UUID.class),
                row.get("run_id", UUID.class),
                row.get("task_id", UUID.class),
                row.get("reserved_credits", BigDecimal.class),
                row.get("actual_credits", BigDecimal.class),
                row.get("reserved_tool_calls", Integer.class),
                row.get("actual_tool_calls", Integer.class),
                row.get("reserved_mcp_calls", Integer.class),
                row.get("actual_mcp_calls", Integer.class),
                instant(row.get("five_hour_bucket_at", OffsetDateTime.class)),
                instant(row.get("weekly_period_start", OffsetDateTime.class)),
                row.get("status", String.class),
                row.get("idempotency_key", String.class),
                instant(row.get("expires_at", OffsetDateTime.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("settled_at", OffsetDateTime.class)));
    }
}
