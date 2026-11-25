package cn.tolim.pudans.infra.r2dbc.ai;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiPriceRule;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiPriceRuleRepository {

    private static final String COLUMNS = """
            SELECT id, target_type, target_key, model_id, mcp_tool_key, pricing_mode,
                   input_coefficient, cached_input_coefficient, output_coefficient,
                   mcp_call_coefficient, unit, unit_credits, base_credits, minimum_credits,
                   peak_windows_jsonb::text AS peak_windows, peak_multiplier,
                   offpeak_multiplier, quota_bucket, effective_from, effective_to,
                   created_at, updated_at
              FROM ai_price_rules
            """;

    private static final String INSERT = """
            INSERT INTO ai_price_rules (
                id, target_type, target_key, model_id, mcp_tool_key, pricing_mode,
                input_coefficient, cached_input_coefficient, output_coefficient,
                mcp_call_coefficient, unit, unit_credits, base_credits, minimum_credits,
                peak_windows_jsonb, peak_multiplier, offpeak_multiplier, quota_bucket,
                effective_from, effective_to, created_at, updated_at)
            VALUES (
                :id, :targetType, :targetKey, :modelId, :mcpToolKey, :pricingMode,
                :inputCoefficient, :cachedInputCoefficient, :outputCoefficient,
                :mcpCallCoefficient, :unit, :unitCredits, :baseCredits, :minimumCredits,
                CAST(:peakWindows AS jsonb), :peakMultiplier, :offpeakMultiplier, :quotaBucket,
                :effectiveFrom, :effectiveTo, :createdAt, :updatedAt)
            """;

    private static final String UPDATE = """
            UPDATE ai_price_rules
               SET target_type = :targetType,
                   target_key = :targetKey,
                   model_id = :modelId,
                   mcp_tool_key = :mcpToolKey,
                   pricing_mode = :pricingMode,
                   input_coefficient = :inputCoefficient,
                   cached_input_coefficient = :cachedInputCoefficient,
                   output_coefficient = :outputCoefficient,
                   mcp_call_coefficient = :mcpCallCoefficient,
                   unit = :unit,
                   unit_credits = :unitCredits,
                   base_credits = :baseCredits,
                   minimum_credits = :minimumCredits,
                   peak_windows_jsonb = CAST(:peakWindows AS jsonb),
                   peak_multiplier = :peakMultiplier,
                   offpeak_multiplier = :offpeakMultiplier,
                   quota_bucket = :quotaBucket,
                   effective_from = :effectiveFrom,
                   effective_to = :effectiveTo,
                   updated_at = :updatedAt
             WHERE id = :id
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiPriceRule> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY target_type, target_key, effective_from DESC")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiPriceRule> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<AiPriceRule> findByTarget(String targetType, String targetKey) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE target_type = :targetType AND target_key = :targetKey
                         ORDER BY effective_from
                        """)
                .bind("targetType", targetType)
                .bind("targetKey", targetKey)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<AiPriceRule> findByModelId(UUID modelId) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE target_type = 'model' AND model_id = :modelId
                         ORDER BY effective_from
                        """)
                .bind("modelId", modelId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<AiPriceRule> findByMcpToolKey(String toolKey) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE target_type = 'mcp_tool' AND mcp_tool_key = :toolKey
                         ORDER BY effective_from
                        """)
                .bind("toolKey", toolKey)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiPriceRule> findEffectiveModel(UUID modelId, Instant at) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE target_type = 'model'
                           AND model_id = :modelId
                           AND effective_from <= :at
                           AND (effective_to IS NULL OR effective_to > :at)
                         ORDER BY effective_from DESC
                         LIMIT 1
                        """)
                .bind("modelId", modelId)
                .bind("at", utc(at))
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiPriceRule> findEffectiveMcp(String toolKey, Instant at) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE target_type = 'mcp_tool'
                           AND mcp_tool_key = :toolKey
                           AND effective_from <= :at
                           AND (effective_to IS NULL OR effective_to > :at)
                         ORDER BY effective_from DESC
                         LIMIT 1
                        """)
                .bind("toolKey", toolKey)
                .bind("at", utc(at))
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiPriceRule> insert(AiPriceRule rule) {
        var spec = baseSpec(databaseClient.sql(INSERT), rule);
        return duplicateAs(
                spec.bind("createdAt", utc(rule.createdAt()))
                        .bind("updatedAt", utc(rule.updatedAt()))
                        .then()
                        .thenReturn(rule),
                "ai-price-rule-duplicate",
                "同一目标与生效时间的价格规则已存在。");
    }

    public Mono<AiPriceRule> update(AiPriceRule rule) {
        var spec = baseSpec(databaseClient.sql(UPDATE), rule);
        return duplicateAs(
                spec.bind("updatedAt", utc(rule.updatedAt()))
                        .then()
                        .thenReturn(rule),
                "ai-price-rule-duplicate",
                "同一目标与生效时间的价格规则已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient.sql("DELETE FROM ai_price_rules WHERE id = :id").bind("id", id).then();
    }

    public Mono<Boolean> existsByModelId(UUID modelId) {
        return exists("SELECT EXISTS (SELECT 1 FROM ai_price_rules WHERE model_id = :id)", modelId);
    }

    public Mono<Boolean> existsByMcpToolKey(String toolKey) {
        return databaseClient
                .sql("SELECT EXISTS (SELECT 1 FROM ai_price_rules WHERE mcp_tool_key = :toolKey)")
                .bind("toolKey", toolKey)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static DatabaseClient.GenericExecuteSpec baseSpec(
            DatabaseClient.GenericExecuteSpec spec, AiPriceRule rule) {
        spec = spec.bind("id", rule.id())
                .bind("targetType", rule.targetType())
                .bind("targetKey", rule.targetKey());
        spec = bindNullable(spec, "modelId", rule.modelId(), UUID.class);
        spec = bindNullable(spec, "mcpToolKey", rule.mcpToolKey(), String.class);
        spec = spec.bind("pricingMode", rule.pricingMode())
                .bind("inputCoefficient", rule.inputCoefficient())
                .bind("cachedInputCoefficient", rule.cachedInputCoefficient())
                .bind("outputCoefficient", rule.outputCoefficient())
                .bind("mcpCallCoefficient", rule.mcpCallCoefficient());
        spec = bindNullable(spec, "unit", rule.unit(), String.class);
        spec = spec.bind("unitCredits", rule.unitCredits())
                .bind("baseCredits", rule.baseCredits())
                .bind("minimumCredits", rule.minimumCredits())
                .bind("peakWindows", AiJsonCodec.write(rule.peakWindows()))
                .bind("peakMultiplier", rule.peakMultiplier())
                .bind("offpeakMultiplier", rule.offpeakMultiplier());
        spec = bindNullable(spec, "quotaBucket", rule.quotaBucket(), String.class);
        spec = spec.bind("effectiveFrom", utc(rule.effectiveFrom()));
        return bindNullable(spec, "effectiveTo", utc(rule.effectiveTo()), OffsetDateTime.class);
    }

    private Mono<Boolean> exists(String sql, UUID id) {
        return databaseClient
                .sql(sql)
                .bind("id", id)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static AiPriceRule map(io.r2dbc.spi.Row row) {
        return new AiPriceRule(
                row.get("id", UUID.class),
                row.get("target_type", String.class),
                row.get("target_key", String.class),
                row.get("model_id", UUID.class),
                row.get("mcp_tool_key", String.class),
                row.get("pricing_mode", String.class),
                row.get("input_coefficient", java.math.BigDecimal.class),
                row.get("cached_input_coefficient", java.math.BigDecimal.class),
                row.get("output_coefficient", java.math.BigDecimal.class),
                row.get("mcp_call_coefficient", java.math.BigDecimal.class),
                row.get("unit", String.class),
                row.get("unit_credits", java.math.BigDecimal.class),
                row.get("base_credits", java.math.BigDecimal.class),
                row.get("minimum_credits", java.math.BigDecimal.class),
                AiJsonCodec.readPeakWindows(row.get("peak_windows", String.class)),
                row.get("peak_multiplier", java.math.BigDecimal.class),
                row.get("offpeak_multiplier", java.math.BigDecimal.class),
                row.get("quota_bucket", String.class),
                instant(row.get("effective_from", OffsetDateTime.class)),
                instant(row.get("effective_to", OffsetDateTime.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
