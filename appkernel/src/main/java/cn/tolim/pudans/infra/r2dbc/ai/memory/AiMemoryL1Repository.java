package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.asset.MemoryL1Item;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import com.fasterxml.jackson.core.type.TypeReference;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryL1Repository {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private static final String SELECT_COLUMNS =
            "id, record_id, team_id, agent_id, user_id, session_id, task_id, conversation_id, "
                    + "content, memory_type, priority, scene_name, source_message_ids, "
                    + "metadata::text AS metadata, dedup_hash, scope, org_candidate, status, version, "
                    + "timestamps, created_at";

    private final DatabaseClient databaseClient;

    public Mono<L1MemoryRecord> insert(L1MemoryRecord record) {
        var context = record.context();
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_l1 (
                            record_id, team_id, agent_id, user_id, session_id, task_id, conversation_id,
                            content, memory_type, priority, scene_name, source_message_ids, metadata,
                            dedup_hash, scope, org_candidate, status, version, timestamps
                        ) VALUES (
                            :recordId, :teamId, :agentId, :userId, :sessionId, :taskId, :conversationId,
                            :content, :memoryType, :priority, :sceneName, :sourceMessageIds,
                            CAST(:metadata AS jsonb), :dedupHash, :scope, :orgCandidate, :status, :version,
                            :timestamps
                        )
                        RETURNING id
                        """)
                .bind("recordId", record.recordId())
                .bind("teamId", context.teamId())
                .bind("agentId", context.agentId())
                .bind("userId", context.userId())
                .bind("sessionId", context.sessionId());
        spec = AiR2dbcBindings.bindNullable(spec, "taskId", context.taskId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "conversationId", record.conversationId(), Long.class);
        spec = spec.bind("content", record.content())
                .bind("memoryType", record.type().name())
                .bind("priority", record.priority())
                .bind("sceneName", record.sceneName())
                .bind("sourceMessageIds", record.sourceMessageIds().toArray(Long[]::new))
                .bind("metadata", JsonUtils.objectToJson(record.metadata()))
                .bind("dedupHash", record.dedupHash())
                .bind("scope", record.scope().name())
                .bind("orgCandidate", record.orgCandidate())
                .bind("status", record.status().name())
                .bind("version", record.version())
                .bind("timestamps", timestamps(record.timestamps()));
        return spec.map((row, metadata) -> record.withId(row.get("id", Long.class))).one();
    }

    public Mono<L1MemoryRecord> updateExisting(L1MemoryRecord record) {
        var spec = databaseClient.sql("""
                        UPDATE ai_memory_l1
                        SET content = :content,
                            memory_type = :memoryType,
                            priority = :priority,
                            scene_name = :sceneName,
                            source_message_ids = :sourceMessageIds,
                            metadata = CAST(:metadata AS jsonb),
                            scope = :scope,
                            org_candidate = :orgCandidate,
                            status = 'ACTIVE',
                            superseded_by = NULL,
                            version = :version,
                            timestamps = :timestamps,
                            updated_at = now()
                        WHERE record_id = :recordId
                        RETURNING id
                        """)
                .bind("recordId", record.recordId())
                .bind("content", record.content())
                .bind("memoryType", record.type().name())
                .bind("priority", record.priority())
                .bind("sceneName", record.sceneName())
                .bind("sourceMessageIds", record.sourceMessageIds().toArray(Long[]::new))
                .bind("metadata", JsonUtils.objectToJson(record.metadata()))
                .bind("scope", record.scope().name())
                .bind("orgCandidate", record.orgCandidate())
                .bind("version", record.version())
                .bind("timestamps", timestamps(record.timestamps()));
        return spec.map((row, metadata) -> record.withId(row.get("id", Long.class))).one();
    }

    public Mono<Long> supersede(List<String> targetRecordIds, long supersededBy) {
        if (targetRecordIds == null || targetRecordIds.isEmpty()) {
            return Mono.just(0L);
        }
        return databaseClient.sql("""
                        UPDATE ai_memory_l1
                        SET status = 'SUPERSEDED', superseded_by = :supersededBy, updated_at = now()
                        WHERE record_id = ANY(:targetRecordIds) AND status <> 'SUPERSEDED'
                        RETURNING id
                        """)
                .bind("supersededBy", supersededBy)
                .bind("targetRecordIds", targetRecordIds.toArray(String[]::new))
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    public Flux<L1MemoryRecord> listByUser(
            String teamId, String agentId, String userId, @Nullable MemoryType type, long beforeId, int limit) {
        var spec = databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE team_id = :teamId
                           AND agent_id = :agentId
                           AND user_id = :userId
                           AND status = 'ACTIVE'
                           AND id < :beforeId
                           AND (:memoryType IS NULL OR memory_type = :memoryType)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(
                spec, "memoryType", type == null ? null : type.name(), String.class);
        return spec.map((row, metadata) -> mapRecord(row)).all();
    }

    public Flux<L1MemoryRecord> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        return databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE id = ANY(:ids)
                         """)
                .bind("ids", ids.toArray(Long[]::new))
                .map((row, metadata) -> mapRecord(row))
                .all();
    }

    public Flux<L1MemoryRecord> findActiveByScope(String teamId, String agentId, long afterId, int limit) {
        return databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE team_id = :teamId
                           AND agent_id = :agentId
                           AND status = 'ACTIVE'
                           AND id > :afterId
                         ORDER BY id
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("afterId", afterId)
                .bind("limit", limit)
                .map((row, metadata) -> mapRecord(row))
                .all();
    }

    public Flux<L1MemoryRecord> findOrgCandidates(String teamId, String agentId, long afterId, int limit) {
        return databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE team_id = :teamId
                           AND agent_id = :agentId
                           AND org_candidate = TRUE
                           AND status = 'ACTIVE'
                           AND id >= :afterId
                         ORDER BY id
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("afterId", afterId)
                .bind("limit", limit)
                .map((row, metadata) -> mapRecord(row))
                .all();
    }

    public Flux<MemoryL1Item> listByScope(
            String teamId,
            String agentId,
            String userId,
            @Nullable Instant from,
            @Nullable Instant to,
            @Nullable String query,
            long beforeId,
            int limit) {
        var spec = databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE team_id = :teamId
                           AND agent_id = :agentId
                           AND user_id = :userId
                           AND status = 'ACTIVE'
                           AND id < :beforeId
                           AND (:from IS NULL OR created_at >= :from)
                           AND (:to IS NULL OR created_at < :to)
                           AND (:pattern IS NULL OR content ILIKE :pattern ESCAPE '\\')
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "from",
                from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "to",
                to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "pattern", query == null ? null : AiMemorySql.likePattern(query), String.class);
        return spec.map((row, metadata) -> mapItem(row)).all();
    }

    public Mono<MemoryL1Item> updateContent(String teamId, String agentId, String userId, long id, String content) {
        return databaseClient.sql("UPDATE ai_memory_l1" + """
                         SET content = :content, version = version + 1, updated_at = now()
                         WHERE id = :id
                           AND team_id = :teamId
                           AND agent_id = :agentId
                           AND user_id = :userId
                           AND status = 'ACTIVE'
                         RETURNING
                         """ + SELECT_COLUMNS)
                .bind("content", content)
                .bind("id", id)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .map((row, metadata) -> mapItem(row))
                .one();
    }

    public Mono<Long> deleteByIds(String teamId, String agentId, String userId, List<Long> ids) {
        return databaseClient.sql("""
                        UPDATE ai_memory_l1
                        SET status = 'DELETED', updated_at = now()
                        WHERE team_id = :teamId
                          AND agent_id = :agentId
                          AND user_id = :userId
                          AND status = 'ACTIVE'
                          AND id = ANY(:ids)
                        RETURNING id
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .bind("ids", ids.toArray(Long[]::new))
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    public Mono<Long> deleteByScope(String teamId, String agentId, String userId) {
        return databaseClient.sql("""
                        UPDATE ai_memory_l1
                        SET status = 'DELETED', updated_at = now()
                        WHERE team_id = :teamId
                          AND agent_id = :agentId
                          AND user_id = :userId
                          AND status = 'ACTIVE'
                        RETURNING id
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    public Mono<Long> countActive(String teamId, String agentId, String userId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM ai_memory_l1
                        WHERE team_id = :teamId AND agent_id = :agentId AND user_id = :userId
                          AND status = 'ACTIVE'
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private static MemoryL1Item mapItem(Row row) {
        return new MemoryL1Item(
                row.get("id", Long.class),
                row.get("record_id", String.class),
                row.get("content", String.class),
                MemoryType.valueOf(row.get("memory_type", String.class)),
                MemoryScope.valueOf(row.get("scope", String.class)),
                row.get("priority", Integer.class),
                row.get("scene_name", String.class),
                row.get("version", Integer.class),
                MemoryStatus.valueOf(row.get("status", String.class)),
                row.get("created_at", Instant.class),
                JsonUtils.jsonToObject(row.get("metadata", String.class), JSON_MAP));
    }

    static L1MemoryRecord mapRecord(Row row) {
        var context = new MemoryContext(
                row.get("team_id", String.class),
                row.get("user_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_id", String.class),
                row.get("task_id", String.class));
        return new L1MemoryRecord(
                row.get("id", Long.class),
                row.get("record_id", String.class),
                context,
                row.get("conversation_id", Long.class),
                row.get("content", String.class),
                MemoryType.valueOf(row.get("memory_type", String.class)),
                row.get("priority", Integer.class),
                row.get("scene_name", String.class),
                longs(row.get("source_message_ids", Long[].class)),
                JsonUtils.jsonToObject(row.get("metadata", String.class), JSON_MAP),
                row.get("dedup_hash", String.class),
                MemoryScope.valueOf(row.get("scope", String.class)),
                Boolean.TRUE.equals(row.get("org_candidate", Boolean.class)),
                MemoryStatus.valueOf(row.get("status", String.class)),
                row.get("version", Integer.class),
                instants(row.get("timestamps", OffsetDateTime[].class)));
    }

    private static OffsetDateTime[] timestamps(List<Instant> timestamps) {
        return timestamps.stream()
                .map(timestamp -> OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC))
                .toArray(OffsetDateTime[]::new);
    }

    private static List<Long> longs(@Nullable Long[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    private static List<Instant> instants(@Nullable OffsetDateTime[] values) {
        return values == null ? List.of() : Arrays.stream(values).map(OffsetDateTime::toInstant).toList();
    }
}
