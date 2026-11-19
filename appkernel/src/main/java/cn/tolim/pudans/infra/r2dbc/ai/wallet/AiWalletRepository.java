package cn.tolim.pudans.infra.r2dbc.ai.wallet;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiWalletRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> ensure(String userId) {
        return databaseClient.sql("""
                        INSERT INTO ai_wallet (user_id)
                        VALUES (:userId)
                        ON CONFLICT (user_id) DO NOTHING
                        """)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<WalletSnapshot> reserve(String userId, BigDecimal amount) {
        return databaseClient.sql("""
                        UPDATE ai_wallet
                        SET frozen = frozen + :amount, updated_at = now()
                        WHERE user_id = :userId
                          AND status = 1
                          AND balance - frozen >= :amount
                        RETURNING balance, frozen
                        """)
                .bind("userId", userId)
                .bind("amount", amount)
                .map((row, metadata) -> snapshot(row))
                .one();
    }

    public Mono<WalletSnapshot> deductForSettle(String userId, BigDecimal frozenAmount, BigDecimal chargedCost) {
        return databaseClient.sql("""
                        UPDATE ai_wallet
                        SET balance = balance - :chargedCost,
                            frozen = frozen - :frozenAmount,
                            total_consumed = total_consumed + :chargedCost,
                            updated_at = now()
                        WHERE user_id = :userId
                          AND frozen >= :frozenAmount
                          AND balance >= :chargedCost
                        RETURNING balance, frozen
                        """)
                .bind("userId", userId)
                .bind("frozenAmount", frozenAmount)
                .bind("chargedCost", chargedCost)
                .map((row, metadata) -> snapshot(row))
                .one();
    }

    public Mono<WalletSnapshot> releaseFrozen(String userId, BigDecimal amount) {
        return databaseClient.sql("""
                        UPDATE ai_wallet
                        SET frozen = frozen - :amount, updated_at = now()
                        WHERE user_id = :userId AND frozen >= :amount
                        RETURNING balance, frozen
                        """)
                .bind("userId", userId)
                .bind("amount", amount)
                .map((row, metadata) -> snapshot(row))
                .one();
    }

    public Mono<WalletSnapshot> findByUserId(String userId) {
        return databaseClient.sql("""
                        SELECT balance, frozen
                        FROM ai_wallet
                        WHERE user_id = :userId
                        """)
                .bind("userId", userId)
                .map((row, metadata) -> snapshot(row))
                .one();
    }

    public Mono<WalletRecord> findDetailByUserId(String userId) {
        return databaseClient.sql("""
                        SELECT balance, frozen, total_granted, total_consumed, status, daily_limit
                        FROM ai_wallet
                        WHERE user_id = :userId
                        """)
                .bind("userId", userId)
                .map((row, metadata) -> new WalletRecord(
                        row.get("balance", BigDecimal.class),
                        row.get("frozen", BigDecimal.class),
                        row.get("total_granted", BigDecimal.class),
                        row.get("total_consumed", BigDecimal.class),
                        row.get("status", Integer.class),
                        row.get("daily_limit", BigDecimal.class)))
                .one();
    }

    public Flux<WalletAdminRecord> list(long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, user_id, balance, frozen, total_granted, total_consumed, status, daily_limit
                        FROM ai_wallet
                        WHERE id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> new WalletAdminRecord(
                        row.get("id", Long.class),
                        row.get("user_id", String.class),
                        row.get("balance", BigDecimal.class),
                        row.get("frozen", BigDecimal.class),
                        row.get("total_granted", BigDecimal.class),
                        row.get("total_consumed", BigDecimal.class),
                        row.get("status", Integer.class),
                        row.get("daily_limit", BigDecimal.class)))
                .all();
    }

    public Mono<WalletSnapshot> adjust(String userId, BigDecimal amount) {
        return databaseClient.sql("""
                        UPDATE ai_wallet
                        SET balance = balance + :amount,
                            total_granted = total_granted + GREATEST(:amount, 0),
                            total_consumed = total_consumed + GREATEST(-:amount, 0),
                            updated_at = now()
                        WHERE user_id = :userId
                          AND status = 1
                          AND balance + :amount >= 0
                        RETURNING balance, frozen
                        """)
                .bind("userId", userId)
                .bind("amount", amount)
                .map((row, metadata) -> snapshot(row))
                .one();
    }

    public Mono<Boolean> setStatus(String userId, int status) {
        return databaseClient.sql("""
                        UPDATE ai_wallet
                        SET status = :status, updated_at = now()
                        WHERE user_id = :userId
                        """)
                .bind("userId", userId)
                .bind("status", status)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    private static WalletSnapshot snapshot(Row row) {
        return new WalletSnapshot(row.get("balance", BigDecimal.class), row.get("frozen", BigDecimal.class));
    }

    public record WalletSnapshot(BigDecimal balance, BigDecimal frozen) {
    }

    public record WalletRecord(
            BigDecimal balance,
            BigDecimal frozen,
            BigDecimal totalGranted,
            BigDecimal totalConsumed,
            int status,
            @Nullable BigDecimal dailyLimit) {
    }

    public record WalletAdminRecord(
            long id,
            String userId,
            BigDecimal balance,
            BigDecimal frozen,
            BigDecimal totalGranted,
            BigDecimal totalConsumed,
            int status,
            @Nullable BigDecimal dailyLimit) {
    }
}
