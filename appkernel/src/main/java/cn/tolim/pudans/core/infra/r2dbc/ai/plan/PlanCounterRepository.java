package cn.tolim.pudans.core.infra.r2dbc.ai.plan;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PlanCounterRepository {

    private static final String COLUMNS =
            "user_id, kind, window_key, starts_at, ends_at, quota_limit, used, reserved, anchor_source, version";

    private final DatabaseClient databaseClient;

    public Mono<Void> ensureRows(String userId) {
        return databaseClient.sql("""
                        INSERT INTO ai_plan_counter
                            (user_id, kind, window_key, starts_at, ends_at, quota_limit)
                        VALUES
                            (:userId, 'FIVE_HOUR', '', now(), now(), 0),
                            (:userId, 'WEEKLY', '', now(), now(), 0)
                        ON CONFLICT (user_id, kind) DO NOTHING
                        """)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<PlanCounterRow> lockCounter(String userId, String kind) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_plan_counter
                         WHERE user_id = :userId AND kind = :kind
                         FOR UPDATE
                         """)
                .bind("userId", userId)
                .bind("kind", kind)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<PlanCounterRow> findByUser(String userId) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                        FROM ai_plan_counter
                        WHERE user_id = :userId
                        """)
                .bind("userId", userId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<Void> resetWindow(
            String userId,
            String kind,
            String windowKey,
            Instant startsAt,
            Instant endsAt,
            long quotaLimit,
            String anchorSource) {
        return databaseClient.sql("""
                        UPDATE ai_plan_counter
                        SET window_key = :windowKey,
                            starts_at = :startsAt,
                            ends_at = :endsAt,
                            used = 0,
                            reserved = 0,
                            quota_limit = :quotaLimit,
                            anchor_source = :anchorSource,
                            version = version + 1,
                            updated_at = now()
                        WHERE user_id = :userId AND kind = :kind
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("windowKey", windowKey)
                .bind("startsAt", timestamp(startsAt))
                .bind("endsAt", timestamp(endsAt))
                .bind("quotaLimit", quotaLimit)
                .bind("anchorSource", anchorSource)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> syncQuotaLimit(String userId, String kind, long quotaLimit) {
        return databaseClient.sql("""
                        UPDATE ai_plan_counter
                        SET quota_limit = :quotaLimit, version = version + 1, updated_at = now()
                        WHERE user_id = :userId AND kind = :kind
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("quotaLimit", quotaLimit)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> reserve(String userId, String kind, String windowKey, long units) {
        return databaseClient.sql("""
                        UPDATE ai_plan_counter
                        SET reserved = reserved + :units, version = version + 1, updated_at = now()
                        WHERE user_id = :userId AND kind = :kind AND window_key = :windowKey
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("windowKey", windowKey)
                .bind("units", units)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> settle(
            String userId, String kind, String windowKey, long reservedUnits, long actualUnits) {
        return databaseClient.sql("""
                        UPDATE ai_plan_counter
                        SET reserved = GREATEST(reserved - :reservedUnits, 0),
                            used = used + :actualUnits,
                            version = version + 1,
                            updated_at = now()
                        WHERE user_id = :userId AND kind = :kind AND window_key = :windowKey
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("windowKey", windowKey)
                .bind("reservedUnits", reservedUnits)
                .bind("actualUnits", actualUnits)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> release(String userId, String kind, String windowKey, long reservedUnits) {
        return databaseClient.sql("""
                        UPDATE ai_plan_counter
                        SET reserved = GREATEST(reserved - :reservedUnits, 0),
                            version = version + 1,
                            updated_at = now()
                        WHERE user_id = :userId AND kind = :kind AND window_key = :windowKey
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("windowKey", windowKey)
                .bind("reservedUnits", reservedUnits)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static PlanCounterRow map(io.r2dbc.spi.Row row) {
        return new PlanCounterRow(
                row.get("user_id", String.class),
                row.get("kind", String.class),
                row.get("window_key", String.class),
                instant(row.get("starts_at", OffsetDateTime.class)),
                instant(row.get("ends_at", OffsetDateTime.class)),
                row.get("quota_limit", Long.class),
                row.get("used", Long.class),
                row.get("reserved", Long.class),
                row.get("anchor_source", String.class),
                row.get("version", Long.class));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value.toInstant();
    }

    public record PlanCounterRow(
            String userId,
            String kind,
            String windowKey,
            Instant startsAt,
            Instant endsAt,
            long quotaLimit,
            long used,
            long reserved,
            String anchorSource,
            long version) {
    }
}