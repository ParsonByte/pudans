package cn.tolim.pudans.infra.r2dbc.ai.quota;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;

import cn.tolim.pudans.contract.ai.AiUsageRecord;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiUsageRecordRepository {

    private final DatabaseClient databaseClient;

    public Mono<AiUsageRecord> insert(AiUsageRecord record) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO ai_usage_records (
                            id, owner_id, workspace_id, run_id, task_id, tool_call_id,
                            model_id, channel_id, mcp_tool_key, units, credits, tool_calls,
                            mcp_calls, pricing_rule_id, window_kind, occurred_at, status,
                            idempotency_key, created_at)
                        VALUES (
                            :id, :ownerId, :workspaceId, :runId, :taskId, :toolCallId,
                            :modelId, :channelId, :mcpToolKey, :units, :credits, :toolCalls,
                            :mcpCalls, :pricingRuleId, :windowKind, :occurredAt, :status,
                            :idempotencyKey, :createdAt)
                        """)
                .bind("id", record.id())
                .bind("ownerId", record.ownerId())
                .bind("workspaceId", record.workspaceId());
        spec = bindNullable(spec, "runId", record.runId(), UUID.class);
        spec = bindNullable(spec, "taskId", record.taskId(), UUID.class);
        spec = bindNullable(spec, "toolCallId", record.toolCallId(), UUID.class);
        spec = bindNullable(spec, "modelId", record.modelId(), UUID.class);
        spec = bindNullable(spec, "channelId", record.channelId(), UUID.class);
        spec = bindNullable(spec, "mcpToolKey", record.mcpToolKey(), String.class);
        spec = spec.bind("units", record.units())
                .bind("credits", record.credits())
                .bind("toolCalls", record.toolCalls())
                .bind("mcpCalls", record.mcpCalls());
        spec = bindNullable(spec, "pricingRuleId", record.pricingRuleId(), UUID.class);
        spec = bindNullable(spec, "windowKind", record.windowKind(), String.class);
        return duplicateAs(
                spec.bind("occurredAt", cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc(record.occurredAt()))
                        .bind("status", record.status())
                        .bind("idempotencyKey", record.idempotencyKey())
                        .bind("createdAt", cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc(record.createdAt()))
                        .then()
                        .thenReturn(record),
                "ai-usage-idempotency-conflict",
                "用量流水已存在。");
    }

    public Mono<AiUsageRecord> findByIdempotencyKey(String idempotencyKey) {
        return databaseClient
                .sql("""
                        SELECT id, owner_id, workspace_id, run_id, task_id, tool_call_id,
                               model_id, channel_id, mcp_tool_key, units, credits, tool_calls,
                               mcp_calls, pricing_rule_id, window_kind, occurred_at, status,
                               idempotency_key, created_at
                          FROM ai_usage_records
                         WHERE idempotency_key = :idempotencyKey
                        """)
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Boolean> existsByPricingRuleId(UUID pricingRuleId) {
        return databaseClient
                .sql("SELECT EXISTS (SELECT 1 FROM ai_usage_records WHERE pricing_rule_id = :id)")
                .bind("id", pricingRuleId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static AiUsageRecord map(io.r2dbc.spi.Row row) {
        return new AiUsageRecord(
                row.get("id", UUID.class),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("run_id", UUID.class),
                row.get("task_id", UUID.class),
                row.get("tool_call_id", UUID.class),
                row.get("model_id", UUID.class),
                row.get("channel_id", UUID.class),
                row.get("mcp_tool_key", String.class),
                row.get("units", java.math.BigDecimal.class),
                row.get("credits", java.math.BigDecimal.class),
                row.get("tool_calls", Integer.class),
                row.get("mcp_calls", Integer.class),
                row.get("pricing_rule_id", UUID.class),
                row.get("window_kind", String.class),
                cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant(
                        row.get("occurred_at", java.time.OffsetDateTime.class)),
                row.get("status", String.class),
                row.get("idempotency_key", String.class),
                cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant(
                        row.get("created_at", java.time.OffsetDateTime.class)));
    }
}
