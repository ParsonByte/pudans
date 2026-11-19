package cn.tolim.pudans.infra.r2dbc.ai.wallet;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiWalletTxnRepository {

    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final DatabaseClient databaseClient;

    public Mono<Boolean> insertIfAbsent(WalletTxn txn) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_wallet_txn (
                            user_id, txn_type, amount, balance_after, request_id, provider_account,
                            model_code, tier, input_tokens, cache_tokens, output_tokens, trace_id,
                            billing_source, idempotency_key
                        ) VALUES (
                            :userId, :txnType, :amount, :balanceAfter, :requestId, :providerAccount,
                            :modelCode, :tier, :inputTokens, :cacheTokens, :outputTokens, :traceId,
                            :billingSource, :idempotencyKey
                        )
                        ON CONFLICT (idempotency_key) DO NOTHING
                        RETURNING id
                        """)
                .bind("userId", txn.userId())
                .bind("txnType", txn.txnType())
                .bind("amount", txn.amount())
                .bind("balanceAfter", txn.balanceAfter());
        spec = bindNullable(spec, "requestId", txn.requestId(), String.class);
        spec = bindNullable(spec, "providerAccount", txn.providerAccount(), String.class);
        spec = bindNullable(spec, "modelCode", txn.modelCode(), String.class);
        spec = bindNullable(spec, "tier", txn.tier(), String.class);
        spec = bindNullable(spec, "inputTokens", txn.inputTokens(), Integer.class);
        spec = bindNullable(spec, "cacheTokens", txn.cacheTokens(), Integer.class);
        spec = bindNullable(spec, "outputTokens", txn.outputTokens(), Integer.class);
        spec = bindNullable(spec, "traceId", txn.traceId(), String.class);
        spec = bindNullable(spec, "billingSource", txn.billingSource(), String.class);
        return spec.bind("idempotencyKey", txn.idempotencyKey())
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<TxnSummary> findByIdempotencyKey(String idempotencyKey) {
        return databaseClient.sql("""
                        SELECT user_id, amount, balance_after, idempotency_key
                        FROM ai_wallet_txn
                        WHERE idempotency_key = :idempotencyKey
                        """)
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> new TxnSummary(
                        row.get("user_id", String.class),
                        row.get("amount", BigDecimal.class),
                        row.get("balance_after", BigDecimal.class),
                        row.get("idempotency_key", String.class)))
                .one();
    }

    public Flux<WalletAdminTxnRecord> listAll(long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, user_id, txn_type, amount, balance_after, request_id,
                               provider_account, model_code, tier, input_tokens, cache_tokens,
                               output_tokens, trace_id, billing_source, idempotency_key, created_at
                        FROM ai_wallet_txn
                        WHERE id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> adminTxnRecord(row))
                .all();
    }

    public Flux<WalletAdminTxnRecord> export(
            @Nullable String userId, @Nullable Instant from, @Nullable Instant to, int limit) {
        return databaseClient.sql("""
                        SELECT id, user_id, txn_type, amount, balance_after, request_id,
                               provider_account, model_code, tier, input_tokens, cache_tokens,
                               output_tokens, trace_id, billing_source, idempotency_key, created_at
                        FROM ai_wallet_txn
                        WHERE (:userId = '' OR user_id = :userId)
                          AND created_at >= :fromAt
                          AND created_at <= :toAt
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("userId", userId == null ? "" : userId)
                .bind("fromAt", OffsetDateTime.ofInstant(from == null ? Instant.EPOCH : from, ZoneOffset.UTC))
                .bind("toAt", OffsetDateTime.ofInstant(to == null ? FAR_FUTURE : to, ZoneOffset.UTC))
                .bind("limit", limit)
                .map((row, metadata) -> adminTxnRecord(row))
                .all();
    }

    public Flux<WalletTxnRecord> listByUser(String userId, long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, txn_type, amount, balance_after, request_id, provider_account,
                               model_code, tier, input_tokens, cache_tokens, output_tokens, trace_id,
                               billing_source, created_at
                        FROM ai_wallet_txn
                        WHERE user_id = :userId
                          AND id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("userId", userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> new WalletTxnRecord(
                        row.get("id", Long.class),
                        row.get("txn_type", String.class),
                        row.get("amount", BigDecimal.class),
                        row.get("balance_after", BigDecimal.class),
                        row.get("request_id", String.class),
                        row.get("provider_account", String.class),
                        row.get("model_code", String.class),
                        row.get("tier", String.class),
                        row.get("input_tokens", Integer.class),
                        row.get("cache_tokens", Integer.class),
                        row.get("output_tokens", Integer.class),
                        row.get("trace_id", String.class),
                        row.get("billing_source", String.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    private static WalletAdminTxnRecord adminTxnRecord(Row row) {
        return new WalletAdminTxnRecord(
                row.get("id", Long.class),
                row.get("user_id", String.class),
                row.get("txn_type", String.class),
                row.get("amount", BigDecimal.class),
                row.get("balance_after", BigDecimal.class),
                row.get("request_id", String.class),
                row.get("provider_account", String.class),
                row.get("model_code", String.class),
                row.get("tier", String.class),
                row.get("input_tokens", Integer.class),
                row.get("cache_tokens", Integer.class),
                row.get("output_tokens", Integer.class),
                row.get("trace_id", String.class),
                row.get("idempotency_key", String.class),
                row.get("billing_source", String.class),
                row.get("created_at", Instant.class));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, @Nullable Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    public record WalletTxn(
            String userId,
            String txnType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            @Nullable String requestId,
            @Nullable String providerAccount,
            @Nullable String modelCode,
            @Nullable String tier,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable String traceId,
            @Nullable String billingSource,
            String idempotencyKey) {
    }

    public record TxnSummary(String userId, BigDecimal amount, BigDecimal balanceAfter, String idempotencyKey) {
    }

    public record WalletTxnRecord(
            long id,
            String txnType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            @Nullable String requestId,
            @Nullable String providerAccount,
            @Nullable String modelCode,
            @Nullable String tier,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable String traceId,
            @Nullable String billingSource,
            Instant createdAt) {
    }

    public record WalletAdminTxnRecord(
            long id,
            String userId,
            String txnType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            @Nullable String requestId,
            @Nullable String providerAccount,
            @Nullable String modelCode,
            @Nullable String tier,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable String traceId,
            String idempotencyKey,
            @Nullable String billingSource,
            Instant createdAt) {
    }
}
