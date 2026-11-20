package cn.tolim.pudans.infra.r2dbc.ai.plan;

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
public class PlanVoucherRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> syncEntitlement(String userId, String kind, String periodKey, int entitlement) {
        return databaseClient.sql("""
                        INSERT INTO ai_plan_voucher_counter (user_id, kind, period_key, entitlement)
                        VALUES (:userId, :kind, :periodKey, :entitlement)
                        ON CONFLICT (user_id, kind, period_key)
                        DO UPDATE SET entitlement = EXCLUDED.entitlement, updated_at = now()
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .bind("entitlement", entitlement)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> addBonus(String userId, String kind, String periodKey, int entitlement, int bonus) {
        return databaseClient.sql("""
                        INSERT INTO ai_plan_voucher_counter (user_id, kind, period_key, entitlement, bonus)
                        VALUES (:userId, :kind, :periodKey, :entitlement, :bonus)
                        ON CONFLICT (user_id, kind, period_key)
                        DO UPDATE SET
                            entitlement = EXCLUDED.entitlement,
                            bonus = ai_plan_voucher_counter.bonus + :bonus,
                            updated_at = now()
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .bind("entitlement", entitlement)
                .bind("bonus", bonus)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<VoucherCounterRow> lockCounter(String userId, String kind, String periodKey) {
        return databaseClient.sql("""
                        SELECT user_id, kind, period_key, entitlement, bonus, used
                        FROM ai_plan_voucher_counter
                        WHERE user_id = :userId AND kind = :kind AND period_key = :periodKey
                        FOR UPDATE
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<VoucherCounterRow> findCounter(String userId, String kind, String periodKey) {
        return databaseClient.sql("""
                        SELECT user_id, kind, period_key, entitlement, bonus, used
                        FROM ai_plan_voucher_counter
                        WHERE user_id = :userId AND kind = :kind AND period_key = :periodKey
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<VoucherCounterRow> consume(String userId, String kind, String periodKey) {
        return databaseClient.sql("""
                        UPDATE ai_plan_voucher_counter
                        SET used = used + 1, updated_at = now()
                        WHERE user_id = :userId AND kind = :kind AND period_key = :periodKey
                          AND entitlement + bonus - used > 0
                        RETURNING user_id, kind, period_key, entitlement, bonus, used
                        """)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Boolean> insertUseIfAbsent(
            String requestId,
            String userId,
            String kind,
            String periodKey,
            String targetWindowKey,
            String windowKeyAfter,
            Instant usedAt) {
        return databaseClient.sql("""
                        INSERT INTO ai_plan_voucher_use
                            (request_id, user_id, kind, period_key, target_window_key, window_key_after, used_at)
                        VALUES
                            (:requestId, :userId, :kind, :periodKey, :targetWindowKey, :windowKeyAfter, :usedAt)
                        ON CONFLICT (request_id) DO NOTHING
                        RETURNING id
                        """)
                .bind("requestId", requestId)
                .bind("userId", userId)
                .bind("kind", kind)
                .bind("periodKey", periodKey)
                .bind("targetWindowKey", targetWindowKey)
                .bind("windowKeyAfter", windowKeyAfter)
                .bind("usedAt", OffsetDateTime.ofInstant(usedAt, ZoneOffset.UTC))
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<VoucherUseRow> findUse(String requestId) {
        return databaseClient.sql("""
                        SELECT id, request_id, user_id, kind, period_key, target_window_key, window_key_after, used_at
                        FROM ai_plan_voucher_use
                        WHERE request_id = :requestId
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> mapUse(row))
                .one();
    }

    public Flux<VoucherUseRow> listUses(String userId, long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, request_id, user_id, kind, period_key, target_window_key, window_key_after, used_at
                        FROM ai_plan_voucher_use
                        WHERE user_id = :userId AND id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("userId", userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapUse(row))
                .all();
    }

    private static VoucherCounterRow map(io.r2dbc.spi.Row row) {
        return new VoucherCounterRow(
                row.get("user_id", String.class),
                row.get("kind", String.class),
                row.get("period_key", String.class),
                row.get("entitlement", Integer.class),
                row.get("bonus", Integer.class),
                row.get("used", Integer.class));
    }

    private static VoucherUseRow mapUse(io.r2dbc.spi.Row row) {
        return new VoucherUseRow(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("user_id", String.class),
                row.get("kind", String.class),
                row.get("period_key", String.class),
                row.get("target_window_key", String.class),
                row.get("window_key_after", String.class),
                row.get("used_at", OffsetDateTime.class).toInstant());
    }

    public record VoucherCounterRow(
            String userId, String kind, String periodKey, int entitlement, int bonus, int used) {

        public int available() {
            return entitlement + bonus - used;
        }
    }

    public record VoucherUseRow(
            long id,
            String requestId,
            String userId,
            String kind,
            String periodKey,
            String targetWindowKey,
            String windowKeyAfter,
            Instant usedAt) {
    }
}