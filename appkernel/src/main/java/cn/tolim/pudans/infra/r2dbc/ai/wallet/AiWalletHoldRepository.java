package cn.tolim.pudans.infra.r2dbc.ai.wallet;

import java.math.BigDecimal;
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
public class AiWalletHoldRepository {

    private final DatabaseClient databaseClient;

    public Mono<Boolean> insertIfAbsent(String requestId, String userId, BigDecimal amount, Instant expiresAt) {
        return databaseClient.sql("""
                        INSERT INTO ai_wallet_hold (request_id, user_id, amount, expires_at)
                        VALUES (:requestId, :userId, :amount, :expiresAt)
                        ON CONFLICT (request_id) DO NOTHING
                        RETURNING request_id
                        """)
                .bind("requestId", requestId)
                .bind("userId", userId)
                .bind("amount", amount)
                .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<Claim> findClaim(String requestId) {
        return databaseClient.sql("""
                        SELECT user_id, amount
                        FROM ai_wallet_hold
                        WHERE request_id = :requestId
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> new Claim(
                        row.get("user_id", String.class), row.get("amount", BigDecimal.class)))
                .one();
    }

    public Mono<Claim> claimSettled(String requestId, String userId) {
        return databaseClient.sql("""
                        UPDATE ai_wallet_hold
                        SET status = 'SETTLED', settled_at = now()
                        WHERE request_id = :requestId AND user_id = :userId AND status = 'HELD'
                        RETURNING user_id, amount
                        """)
                .bind("requestId", requestId)
                .bind("userId", userId)
                .map((row, metadata) -> new Claim(
                        row.get("user_id", String.class), row.get("amount", BigDecimal.class)))
                .one();
    }

    public Mono<Claim> claimReleased(String requestId) {
        return databaseClient.sql("""
                        UPDATE ai_wallet_hold
                        SET status = 'RELEASED', released_at = now()
                        WHERE request_id = :requestId AND status = 'HELD'
                        RETURNING user_id, amount
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> new Claim(
                        row.get("user_id", String.class), row.get("amount", BigDecimal.class)))
                .one();
    }

    public Mono<Boolean> deleteIfHeld(String requestId) {
        return databaseClient.sql("""
                        DELETE FROM ai_wallet_hold
                        WHERE request_id = :requestId AND status = 'HELD'
                        RETURNING request_id
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    public Flux<String> findExpiredRequestIds(int limit) {
        return databaseClient.sql("""
                        SELECT request_id
                        FROM ai_wallet_hold
                        WHERE status = 'HELD' AND expires_at <= now()
                        ORDER BY expires_at
                        LIMIT :limit
                        """)
                .bind("limit", limit)
                .map((row, metadata) -> row.get("request_id", String.class))
                .all();
    }

    public record Claim(String userId, BigDecimal amount) {
    }
}
