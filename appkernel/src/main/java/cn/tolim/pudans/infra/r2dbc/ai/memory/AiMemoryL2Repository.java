package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryL2Repository {

    private final DatabaseClient databaseClient;

    public Mono<L2SceneBlock> upsert(L2SceneBlock block) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_l2 (
                            team_id, agent_id, scene_key, title, summary, l1_ids, heat, confidence, last_active_at
                        ) VALUES (
                            :teamId, :agentId, :sceneKey, :title, :summary, :l1Ids, :heat, :confidence,
                            COALESCE(:lastActiveAt, now())
                        )
                        ON CONFLICT (team_id, agent_id, scene_key) DO UPDATE SET
                            title = EXCLUDED.title,
                            summary = EXCLUDED.summary,
                            l1_ids = EXCLUDED.l1_ids,
                            heat = EXCLUDED.heat,
                            confidence = EXCLUDED.confidence,
                            last_active_at = EXCLUDED.last_active_at,
                            updated_at = now()
                        RETURNING id, last_active_at
                        """)
                .bind("teamId", block.teamId())
                .bind("agentId", block.agentId())
                .bind("sceneKey", block.sceneKey())
                .bind("title", block.title())
                .bind("summary", block.summary())
                .bind("l1Ids", block.l1Ids().toArray(Long[]::new))
                .bind("heat", block.heat())
                .bind("confidence", block.confidence());
        var lastActiveAt = block.lastActiveAt();
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "lastActiveAt",
                lastActiveAt == null ? null : OffsetDateTime.ofInstant(lastActiveAt, ZoneOffset.UTC),
                OffsetDateTime.class);
        return spec.map((row, metadata) -> new L2SceneBlock(
                        row.get("id", Long.class),
                        block.teamId(),
                        block.agentId(),
                        block.sceneKey(),
                        block.title(),
                        block.summary(),
                        block.l1Ids(),
                        block.heat(),
                        block.confidence(),
                        row.get("last_active_at", Instant.class)))
                .one();
    }

    public Mono<L2SceneBlock> findByKey(String teamId, String agentId, String sceneKey) {
        return databaseClient.sql("""
                        SELECT id, team_id, agent_id, scene_key, title, summary, l1_ids, heat,
                               confidence, last_active_at
                        FROM ai_memory_l2
                        WHERE team_id = :teamId AND agent_id = :agentId AND scene_key = :sceneKey
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("sceneKey", sceneKey)
                .map((row, metadata) -> mapBlock(row))
                .one();
    }

    public Flux<L2SceneBlock> findByScope(String teamId, String agentId) {
        return databaseClient.sql("""
                        SELECT id, team_id, agent_id, scene_key, title, summary, l1_ids, heat, confidence,
                               last_active_at
                        FROM ai_memory_l2
                        WHERE team_id = :teamId AND agent_id = :agentId
                        ORDER BY last_active_at DESC, id
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .map((row, metadata) -> mapBlock(row))
                .all();
    }

    static L2SceneBlock mapBlock(Row row) {
        return new L2SceneBlock(
                row.get("id", Long.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("scene_key", String.class),
                row.get("title", String.class),
                row.get("summary", String.class),
                longs(row.get("l1_ids", Long[].class)),
                row.get("heat", Integer.class),
                row.get("confidence", BigDecimal.class),
                row.get("last_active_at", Instant.class));
    }

    private static List<Long> longs(@Nullable Long[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }
}
