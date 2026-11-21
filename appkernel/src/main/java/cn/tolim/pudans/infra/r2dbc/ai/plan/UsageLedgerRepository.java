package cn.tolim.pudans.infra.r2dbc.ai.plan;

import static cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings.bindNullable;

import cn.tolim.pudans.core.ai.plan.PlanUsageSnapshot;
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
public class UsageLedgerRepository {

    private static final String COLUMNS =
            "id, request_id, user_id, team_id, plan_id, billing_scope, request_kind, "
                    + "five_hour_window_key, weekly_window_key, provider, provider_account, model_code, tier, "
                    + "input_tokens, cache_tokens, output_tokens, reserved_units, units, status, trace_id, "
                    + "created_at, settled_at";

    private final DatabaseClient databaseClient;

    public Mono<Boolean> insertReserved(
            String requestId,
            String userId,
            @Nullable String teamId,
            @Nullable String planId,
            String billingScope,
            String requestKind,
            @Nullable String fiveHourWindowKey,
            @Nullable String weeklyWindowKey,
            long reservedUnits,
            @Nullable String traceId) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_usage_ledger
                            (request_id, user_id, team_id, plan_id, billing_scope, request_kind,
                             five_hour_window_key, weekly_window_key, reserved_units, units, status, trace_id)
                        VALUES
                            (:requestId, :userId, :teamId, :planId, :billingScope, :requestKind,
                             :fiveHourWindowKey, :weeklyWindowKey, :reservedUnits, :reservedUnits,
                             'RESERVED', :traceId)
                        ON CONFLICT (request_id) DO NOTHING
                        RETURNING id
                        """)
                .bind("requestId", requestId)
                .bind("userId", userId)
                .bind("billingScope", billingScope)
                .bind("requestKind", requestKind)
                .bind("reservedUnits", reservedUnits);
        spec = bindNullable(spec, "teamId", teamId, String.class);
        spec = bindNullable(spec, "planId", planId, String.class);
        spec = bindNullable(spec, "fiveHourWindowKey", fiveHourWindowKey, String.class);
        spec = bindNullable(spec, "weeklyWindowKey", weeklyWindowKey, String.class);
        spec = bindNullable(spec, "traceId", traceId, String.class);
        return spec.map((row, metadata) -> Boolean.TRUE).one().defaultIfEmpty(false);
    }

    public Mono<LedgerClaim> claimSettled(
            String requestId, long actualUnits, PlanUsageSnapshot snapshot) {
        var spec = databaseClient.sql("""
                        UPDATE ai_usage_ledger
                        SET status = 'SETTLED',
                            units = :actualUnits,
                            provider = :provider,
                            provider_account = :providerAccount,
                            model_code = :modelCode,
                            tier = :tier,
                            input_tokens = :inputTokens,
                            cache_tokens = :cacheTokens,
                            output_tokens = :outputTokens,
                            trace_id = COALESCE(:traceId, trace_id),
                            settled_at = now()
                        WHERE request_id = :requestId AND status = 'RESERVED'
                        RETURNING user_id, plan_id, reserved_units, five_hour_window_key, weekly_window_key
                        """)
                .bind("requestId", requestId)
                .bind("actualUnits", actualUnits);
        spec = bindNullable(spec, "provider", snapshot.provider(), String.class);
        spec = bindNullable(spec, "providerAccount", snapshot.providerAccount(), String.class);
        spec = bindNullable(spec, "modelCode", snapshot.modelCode(), String.class);
        spec = bindNullable(spec, "tier", snapshot.tier() == null ? null : snapshot.tier().name(), String.class);
        spec = bindNullable(spec, "inputTokens", snapshot.inputTokens(), Integer.class);
        spec = bindNullable(spec, "cacheTokens", snapshot.cacheTokens(), Integer.class);
        spec = bindNullable(spec, "outputTokens", snapshot.outputTokens(), Integer.class);
        spec = bindNullable(spec, "traceId", snapshot.traceId(), String.class);
        return spec.map((row, metadata) -> claim(row)).one();
    }

    public Mono<LedgerClaim> claimReleased(String requestId) {
        return databaseClient.sql("""
                        UPDATE ai_usage_ledger
                        SET status = 'RELEASED', settled_at = now()
                        WHERE request_id = :requestId AND status = 'RESERVED'
                        RETURNING user_id, plan_id, reserved_units, five_hour_window_key, weekly_window_key
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> claim(row))
                .one();
    }

    public Mono<LedgerRow> findByRequestId(String requestId) {
        return databaseClient.sql("SELECT " + COLUMNS + "\n" + """
                         FROM ai_usage_ledger
                         WHERE request_id = :requestId
                         """)
                .bind("requestId", requestId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<String> findStaleReserved(Instant cutoff, int limit) {
        return databaseClient.sql("""
                        SELECT request_id
                        FROM ai_usage_ledger
                        WHERE status = 'RESERVED' AND created_at < :cutoff
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .bind("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .bind("limit", limit)
                .map((row, metadata) -> row.get("request_id", String.class))
                .all();
    }

    private static LedgerClaim claim(io.r2dbc.spi.Row row) {
        return new LedgerClaim(
                row.get("user_id", String.class),
                row.get("plan_id", String.class),
                row.get("reserved_units", Long.class),
                row.get("five_hour_window_key", String.class),
                row.get("weekly_window_key", String.class));
    }

    private static LedgerRow map(io.r2dbc.spi.Row row) {
        return new LedgerRow(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("plan_id", String.class),
                row.get("billing_scope", String.class),
                row.get("request_kind", String.class),
                row.get("five_hour_window_key", String.class),
                row.get("weekly_window_key", String.class),
                row.get("provider", String.class),
                row.get("provider_account", String.class),
                row.get("model_code", String.class),
                row.get("tier", String.class),
                row.get("input_tokens", Integer.class),
                row.get("cache_tokens", Integer.class),
                row.get("output_tokens", Integer.class),
                row.get("reserved_units", Long.class),
                row.get("units", Long.class),
                row.get("status", String.class),
                row.get("trace_id", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instantOrNull(row.get("settled_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value.toInstant();
    }

    private static @Nullable Instant instantOrNull(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record LedgerClaim(
            String userId,
            @Nullable String planId,
            long reservedUnits,
            @Nullable String fiveHourWindowKey,
            @Nullable String weeklyWindowKey) {
    }

    public record LedgerRow(
            long id,
            String requestId,
            String userId,
            @Nullable String teamId,
            @Nullable String planId,
            String billingScope,
            String requestKind,
            @Nullable String fiveHourWindowKey,
            @Nullable String weeklyWindowKey,
            @Nullable String provider,
            @Nullable String providerAccount,
            @Nullable String modelCode,
            @Nullable String tier,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            long reservedUnits,
            long units,
            String status,
            @Nullable String traceId,
            Instant createdAt,
            @Nullable Instant settledAt) {
    }
}