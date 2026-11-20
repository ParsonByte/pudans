package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import com.fasterxml.jackson.core.type.TypeReference;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryL3Repository {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final DatabaseClient databaseClient;

    public Mono<L3Persona> upsert(L3Persona persona) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_l3 (
                            team_id, agent_id, persona_json, persona_text, source_l1_count,
                            source_l2_count, confidence, last_refreshed_at
                        ) VALUES (
                            :teamId, :agentId, CAST(:personaJson AS jsonb), :personaText, :sourceL1Count,
                            :sourceL2Count, :confidence, :lastRefreshedAt
                        )
                        ON CONFLICT (team_id, agent_id) DO UPDATE SET
                            persona_json = EXCLUDED.persona_json,
                            persona_text = EXCLUDED.persona_text,
                            source_l1_count = EXCLUDED.source_l1_count,
                            source_l2_count = EXCLUDED.source_l2_count,
                            confidence = EXCLUDED.confidence,
                            last_refreshed_at = EXCLUDED.last_refreshed_at,
                            updated_at = now()
                        RETURNING id
                        """)
                .bind("teamId", persona.teamId())
                .bind("agentId", persona.agentId())
                .bind("personaJson", JsonUtils.objectToJson(persona.personaJson()))
                .bind("personaText", persona.personaText())
                .bind("sourceL1Count", persona.sourceL1Count())
                .bind("sourceL2Count", persona.sourceL2Count())
                .bind("confidence", persona.confidence());
        var lastRefreshedAt = persona.lastRefreshedAt();
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "lastRefreshedAt",
                lastRefreshedAt == null ? null : OffsetDateTime.ofInstant(lastRefreshedAt, ZoneOffset.UTC),
                OffsetDateTime.class);
        return spec.map((row, metadata) -> persona.withId(row.get("id", Long.class))).one();
    }

    public Mono<L3Persona> findByScope(String teamId, String agentId) {
        return databaseClient.sql("""
                        SELECT id, team_id, agent_id, persona_json::text AS persona_json, persona_text,
                               source_l1_count, source_l2_count, confidence, last_refreshed_at
                        FROM ai_memory_l3
                        WHERE team_id = :teamId AND agent_id = :agentId
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .map((row, metadata) -> mapPersona(row))
                .one();
    }

    public Mono<L3Persona> overwriteText(String teamId, String agentId, String text) {
        return databaseClient.sql("""
                        INSERT INTO ai_memory_l3 (
                            team_id, agent_id, persona_json, persona_text, source_l1_count,
                            source_l2_count, confidence, last_refreshed_at
                        ) VALUES (
                            :teamId, :agentId, '{}'::jsonb, :personaText, 0, 0, 1.0, now()
                        )
                        ON CONFLICT (team_id, agent_id) DO UPDATE SET
                            persona_json = '{}'::jsonb,
                            persona_text = EXCLUDED.persona_text,
                            last_refreshed_at = now(),
                            updated_at = now()
                        RETURNING id, team_id, agent_id, persona_json::text AS persona_json, persona_text,
                                  source_l1_count, source_l2_count, confidence, last_refreshed_at
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("personaText", text)
                .map((row, metadata) -> mapPersona(row))
                .one();
    }

    public Mono<Long> countByScope(String teamId, String agentId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM ai_memory_l3
                        WHERE team_id = :teamId AND agent_id = :agentId
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    public Mono<Long> deleteByScope(String teamId, String agentId) {
        return databaseClient.sql("""
                        DELETE FROM ai_memory_l3
                        WHERE team_id = :teamId AND agent_id = :agentId
                        RETURNING id
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    static L3Persona mapPersona(Row row) {
        return new L3Persona(
                row.get("id", Long.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                JsonUtils.jsonToObject(row.get("persona_json", String.class), JSON_MAP),
                row.get("persona_text", String.class),
                row.get("source_l1_count", Integer.class),
                row.get("source_l2_count", Integer.class),
                row.get("confidence", BigDecimal.class),
                row.get("last_refreshed_at", Instant.class));
    }
}
