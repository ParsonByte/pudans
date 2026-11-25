package cn.tolim.pudans.infra.r2dbc.ai.quota;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiQuotaPlanRepository {

    private static final String COLUMNS = """
            SELECT id, name, display_name, five_hour_credits, weekly_credits,
                   five_hour_tool_calls, weekly_tool_calls, five_hour_mcp_calls,
                   weekly_mcp_calls, five_hour_window_seconds, weekly_window_days,
                   weekly_anchor_mode, status, created_at, updated_at
              FROM ai_quota_plans
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiQuotaPlan> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY name, id")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiQuotaPlan> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiQuotaPlan> insert(AiQuotaPlan plan) {
        return duplicateAs(
                databaseClient
                        .sql("""
                                INSERT INTO ai_quota_plans (
                                    id, name, display_name, five_hour_credits, weekly_credits,
                                    five_hour_tool_calls, weekly_tool_calls, five_hour_mcp_calls,
                                    weekly_mcp_calls, five_hour_window_seconds, weekly_window_days,
                                    weekly_anchor_mode, status, created_at, updated_at)
                                VALUES (
                                    :id, :name, :displayName, :fiveHourCredits, :weeklyCredits,
                                    :fiveHourToolCalls, :weeklyToolCalls, :fiveHourMcpCalls,
                                    :weeklyMcpCalls, :fiveHourWindowSeconds, :weeklyWindowDays,
                                    :weeklyAnchorMode, :status, :createdAt, :updatedAt)
                                """)
                        .bind("id", plan.id())
                        .bind("name", plan.name())
                        .bind("displayName", plan.displayName())
                        .bind("fiveHourCredits", plan.fiveHourCredits())
                        .bind("weeklyCredits", plan.weeklyCredits())
                        .bind("fiveHourToolCalls", plan.fiveHourToolCalls())
                        .bind("weeklyToolCalls", plan.weeklyToolCalls())
                        .bind("fiveHourMcpCalls", plan.fiveHourMcpCalls())
                        .bind("weeklyMcpCalls", plan.weeklyMcpCalls())
                        .bind("fiveHourWindowSeconds", plan.fiveHourWindowSeconds())
                        .bind("weeklyWindowDays", plan.weeklyWindowDays())
                        .bind("weeklyAnchorMode", plan.weeklyAnchorMode())
                        .bind("status", plan.status())
                        .bind("createdAt", utc(plan.createdAt()))
                        .bind("updatedAt", utc(plan.updatedAt()))
                        .then()
                        .thenReturn(plan),
                "ai-quota-plan-name-conflict",
                "配额套餐名称已存在。");
    }

    public Mono<AiQuotaPlan> update(AiQuotaPlan plan) {
        return duplicateAs(
                databaseClient
                        .sql("""
                                UPDATE ai_quota_plans
                                   SET name = :name,
                                       display_name = :displayName,
                                       five_hour_credits = :fiveHourCredits,
                                       weekly_credits = :weeklyCredits,
                                       five_hour_tool_calls = :fiveHourToolCalls,
                                       weekly_tool_calls = :weeklyToolCalls,
                                       five_hour_mcp_calls = :fiveHourMcpCalls,
                                       weekly_mcp_calls = :weeklyMcpCalls,
                                       five_hour_window_seconds = :fiveHourWindowSeconds,
                                       weekly_window_days = :weeklyWindowDays,
                                       weekly_anchor_mode = :weeklyAnchorMode,
                                       status = :status,
                                       updated_at = :updatedAt
                                 WHERE id = :id
                                """)
                        .bind("id", plan.id())
                        .bind("name", plan.name())
                        .bind("displayName", plan.displayName())
                        .bind("fiveHourCredits", plan.fiveHourCredits())
                        .bind("weeklyCredits", plan.weeklyCredits())
                        .bind("fiveHourToolCalls", plan.fiveHourToolCalls())
                        .bind("weeklyToolCalls", plan.weeklyToolCalls())
                        .bind("fiveHourMcpCalls", plan.fiveHourMcpCalls())
                        .bind("weeklyMcpCalls", plan.weeklyMcpCalls())
                        .bind("fiveHourWindowSeconds", plan.fiveHourWindowSeconds())
                        .bind("weeklyWindowDays", plan.weeklyWindowDays())
                        .bind("weeklyAnchorMode", plan.weeklyAnchorMode())
                        .bind("status", plan.status())
                        .bind("updatedAt", utc(plan.updatedAt()))
                        .then()
                        .thenReturn(plan),
                "ai-quota-plan-name-conflict",
                "配额套餐名称已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient.sql("DELETE FROM ai_quota_plans WHERE id = :id").bind("id", id).then();
    }

    private static AiQuotaPlan map(io.r2dbc.spi.Row row) {
        return new AiQuotaPlan(
                row.get("id", UUID.class),
                row.get("name", String.class),
                row.get("display_name", String.class),
                row.get("five_hour_credits", java.math.BigDecimal.class),
                row.get("weekly_credits", java.math.BigDecimal.class),
                row.get("five_hour_tool_calls", Integer.class),
                row.get("weekly_tool_calls", Integer.class),
                row.get("five_hour_mcp_calls", Integer.class),
                row.get("weekly_mcp_calls", Integer.class),
                row.get("five_hour_window_seconds", Integer.class),
                row.get("weekly_window_days", Integer.class),
                row.get("weekly_anchor_mode", String.class),
                row.get("status", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
