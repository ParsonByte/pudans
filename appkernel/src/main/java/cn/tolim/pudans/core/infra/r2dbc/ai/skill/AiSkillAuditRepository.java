package cn.tolim.pudans.core.infra.r2dbc.ai.skill;

import cn.tolim.pudans.core.ai.harness.skill.curator.SkillAuditLog;
import io.r2dbc.spi.Row;
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
public class AiSkillAuditRepository {

    private final DatabaseClient databaseClient;

    public Mono<SkillAuditLog.Entry> insert(SkillAuditLog.Entry entry) {
        return databaseClient.sql("""
                        INSERT INTO ai_skill_audit (occurred_at, actor, operation, target, decision, detail)
                        VALUES (:occurredAt, :actor, :operation, :target, :decision, :detail)
                        RETURNING id
                        """)
                .bind("occurredAt", OffsetDateTime.ofInstant(entry.at(), ZoneOffset.UTC))
                .bind("actor", entry.actor())
                .bind("operation", entry.operation())
                .bind("target", entry.target())
                .bind("decision", entry.decision())
                .bind("detail", entry.detail())
                .map((row, metadata) -> entry)
                .one();
    }

    public Flux<SkillAuditLog.Entry> recent(int limit) {
        return databaseClient.sql("""
                        SELECT occurred_at, actor, operation, target, decision, detail
                        FROM ai_skill_audit
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT :limit
                        """)
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> map(row))
                .all();
    }

    private static SkillAuditLog.Entry map(Row row) {
        return new SkillAuditLog.Entry(
                row.get("occurred_at", Instant.class),
                row.get("actor", String.class),
                row.get("operation", String.class),
                row.get("target", String.class),
                row.get("decision", String.class),
                row.get("detail", String.class));
    }
}