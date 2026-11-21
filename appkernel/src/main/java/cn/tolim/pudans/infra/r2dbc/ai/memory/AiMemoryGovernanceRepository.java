package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
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
public class AiMemoryGovernanceRepository {

    private static final String L1_COLUMNS =
            "id, record_id, team_id, agent_id, user_id, session_id, task_id, conversation_id, "
                    + "content, memory_type, priority, scene_name, source_message_ids, "
                    + "metadata::text AS metadata, dedup_hash, scope, org_candidate, status, version, timestamps";

    private static final String REQUEST_COLUMNS =
            "id, user_id, team_id, agent_id, memory_level, memory_id, request_type, reason, "
                    + "suggested_content, status, reviewed_by, reviewed_at, review_comment, created_at";

    private static final String ORG_COLUMNS =
            "id, content, content_type, dedup_hash, trust_level, source_type, source_team_ids, "
                    + "source_user_ids, source_memory_ids, cross_vote_count, approved_by, approved_at, "
                    + "is_active, created_at, updated_at";

    private static final String EXPORT_COLUMNS =
            "id, record_id, team_id, agent_id, user_id, scene_name, memory_type, priority, status, "
                    + "scope, version, decay_score, last_accessed_at, created_at, content";

    private final DatabaseClient databaseClient;

    public Flux<L1MemoryRecord> listL1(
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String userId,
            @Nullable MemoryType type,
            @Nullable MemoryStatus status,
            long beforeId,
            int limit) {
        var spec = databaseClient.sql("SELECT " + L1_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE id < :beforeId
                           AND (:teamId IS NULL OR team_id = :teamId)
                           AND (:agentId IS NULL OR agent_id = :agentId)
                           AND (:userId IS NULL OR user_id = :userId)
                           AND (:memoryType IS NULL OR memory_type = :memoryType)
                           AND (:memoryStatus IS NULL OR status = :memoryStatus)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", teamId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", agentId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", userId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "memoryType", type == null ? null : type.name(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "memoryStatus", status == null ? null : status.name(), String.class);
        return spec.map((row, metadata) -> AiMemoryL1Repository.mapRecord(row)).all();
    }

    public Mono<L1MemoryRecord> findL1ById(long id) {
        return databaseClient.sql("SELECT " + L1_COLUMNS + " FROM ai_memory_l1 WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> AiMemoryL1Repository.mapRecord(row))
                .one();
    }

    public Flux<MemoryExportRow> exportL1(
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String userId,
            @Nullable MemoryType type,
            @Nullable MemoryStatus status,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        var spec = databaseClient.sql("SELECT " + EXPORT_COLUMNS + """
                         FROM ai_memory_l1
                         WHERE (:teamId IS NULL OR team_id = :teamId)
                           AND (:agentId IS NULL OR agent_id = :agentId)
                           AND (:userId IS NULL OR user_id = :userId)
                           AND (:memoryType IS NULL OR memory_type = :memoryType)
                           AND (:memoryStatus IS NULL OR status = :memoryStatus)
                           AND (:fromAt IS NULL OR created_at >= :fromAt)
                           AND (:toAt IS NULL OR created_at <= :toAt)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", teamId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", agentId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", userId, String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "memoryType", type == null ? null : type.name(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "memoryStatus", status == null ? null : status.name(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "fromAt", offsetDateTime(from), OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(spec, "toAt", offsetDateTime(to), OffsetDateTime.class);
        return spec.map((row, metadata) -> mapExport(row)).all();
    }

    public Mono<L1MemoryRecord> updateL1Status(long id, MemoryStatus status) {
        return databaseClient.sql("UPDATE ai_memory_l1 SET status = :status, updated_at = now() "
                        + "WHERE id = :id RETURNING " + L1_COLUMNS)
                .bind("status", status.name())
                .bind("id", id)
                .map((row, metadata) -> AiMemoryL1Repository.mapRecord(row))
                .one();
    }

    public Mono<L1MemoryRecord> updateL1ContentAndStatus(
            long id, String content, @Nullable Map<String, Object> metadata, MemoryStatus status) {
        var spec = databaseClient.sql("UPDATE ai_memory_l1 SET content = :content, "
                        + "metadata = COALESCE(CAST(:metadata AS jsonb), metadata), status = :status, "
                        + "version = version + 1, updated_at = now() "
                        + "WHERE id = :id RETURNING " + L1_COLUMNS)
                .bind("content", content)
                .bind("status", status.name())
                .bind("id", id);
        spec = AiR2dbcBindings.bindNullable(
                spec, "metadata", metadata == null ? null : JsonUtils.objectToJson(metadata), String.class);
        return spec.map((row, rowMetadata) -> AiMemoryL1Repository.mapRecord(row)).one();
    }

    public Mono<Long> insertSnapshot(
            long memoryId,
            String ownerUserId,
            @Nullable String teamId,
            @Nullable String agentId,
            String snapshot,
            int retentionDays) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_snapshot (
                            memory_level, memory_id, owner_user_id, team_id, agent_id, snapshot, expires_at
                        ) VALUES (
                            'L1', :memoryId, :ownerUserId, :teamId, :agentId, CAST(:snapshot AS jsonb),
                            now() + make_interval(days => :retentionDays)
                        )
                        RETURNING id
                        """)
                .bind("memoryId", memoryId)
                .bind("ownerUserId", ownerUserId)
                .bind("snapshot", snapshot)
                .bind("retentionDays", retentionDays);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", teamId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", agentId, String.class);
        return spec.map((row, metadata) -> row.get("id", Long.class)).one();
    }

    public Mono<String> findLatestSnapshot(long memoryId) {
        return databaseClient.sql("""
                        SELECT snapshot::text AS snapshot
                        FROM ai_memory_snapshot
                        WHERE memory_level = 'L1' AND memory_id = :memoryId
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """)
                .bind("memoryId", memoryId)
                .map((row, metadata) -> row.get("snapshot", String.class))
                .one();
    }

    public Mono<MemoryRequestRecord> insertRequest(NewMemoryRequest request) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_request (
                            user_id, team_id, agent_id, memory_level, memory_id, request_type,
                            reason, suggested_content, status
                        ) VALUES (
                            :userId, :teamId, :agentId, :memoryLevel, :memoryId, :requestType,
                            :reason, :suggestedContent, 'PENDING'
                        )
                        RETURNING
                        """ + REQUEST_COLUMNS)
                .bind("userId", request.userId())
                .bind("memoryLevel", request.memoryLevel())
                .bind("memoryId", request.memoryId())
                .bind("requestType", request.requestType())
                .bind("reason", request.reason());
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", request.teamId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", request.agentId(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "suggestedContent", request.suggestedContent(), String.class);
        return spec.map((row, metadata) -> mapRequest(row)).one();
    }

    public Mono<Boolean> existsPendingRequest(
            String userId, String memoryLevel, long memoryId, String requestType) {
        return databaseClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM ai_memory_request
                            WHERE user_id = :userId
                              AND memory_level = :memoryLevel
                              AND memory_id = :memoryId
                              AND request_type = :requestType
                              AND status = 'PENDING'
                        ) AS pending
                        """)
                .bind("userId", userId)
                .bind("memoryLevel", memoryLevel)
                .bind("memoryId", memoryId)
                .bind("requestType", requestType)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("pending", Boolean.class)))
                .one();
    }

    public Mono<MemoryRequestRecord> findRequestById(long id) {
        return databaseClient.sql("SELECT " + REQUEST_COLUMNS + " FROM ai_memory_request WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> mapRequest(row))
                .one();
    }

    public Flux<MemoryRequestRecord> listRequests(
            @Nullable String userId, @Nullable String status, long beforeId, int limit) {
        var spec = databaseClient.sql("SELECT " + REQUEST_COLUMNS + """
                         FROM ai_memory_request
                         WHERE id < :beforeId
                           AND (:userId IS NULL OR user_id = :userId)
                           AND (:status IS NULL OR status = :status)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", userId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "status", status, String.class);
        return spec.map((row, metadata) -> mapRequest(row)).all();
    }

    public Mono<MemoryRequestRecord> updateRequestStatus(
            long id, String status, String reviewedBy, @Nullable String comment) {
        var spec = databaseClient.sql("UPDATE ai_memory_request "
                        + "SET status = :status, reviewed_by = :reviewedBy, reviewed_at = now(), "
                        + "review_comment = :comment "
                        + "WHERE id = :id AND status = 'PENDING' RETURNING " + REQUEST_COLUMNS)
                .bind("status", status)
                .bind("reviewedBy", reviewedBy)
                .bind("id", id);
        spec = AiR2dbcBindings.bindNullable(spec, "comment", comment, String.class);
        return spec.map((row, metadata) -> mapRequest(row)).one();
    }

    public Flux<OrgMemoryRecord> listOrgMemories(String trustLevel, long beforeId, int limit) {
        return databaseClient.sql("SELECT " + ORG_COLUMNS + """
                        FROM ai_org_memory
                        WHERE trust_level = :trustLevel
                          AND is_active = TRUE
                          AND id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("trustLevel", trustLevel)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapOrgMemory(row))
                .all();
    }

    public Mono<OrgMemoryRecord> findOrgMemory(long id) {
        return databaseClient.sql("SELECT " + ORG_COLUMNS + " FROM ai_org_memory WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> mapOrgMemory(row))
                .one();
    }

    public Mono<OrgMemoryRecord> updateOrgMemoryStatus(
            long id, String trustLevel, boolean active, String reviewer, boolean markApprovedAt) {
        return databaseClient.sql("UPDATE ai_org_memory "
                        + "SET trust_level = :trustLevel, is_active = :active, approved_by = :reviewer, "
                        + "approved_at = CASE WHEN :markApprovedAt THEN now() ELSE approved_at END, "
                        + "updated_at = now() "
                        + "WHERE id = :id RETURNING " + ORG_COLUMNS)
                .bind("trustLevel", trustLevel)
                .bind("active", active)
                .bind("reviewer", reviewer)
                .bind("markApprovedAt", markApprovedAt)
                .bind("id", id)
                .map((row, metadata) -> mapOrgMemory(row))
                .one();
    }

    public Flux<TaskStatusCount> taskStatusCounts() {
        return databaseClient.sql("""
                        SELECT task_type, status::int AS status, COUNT(*) AS count
                        FROM ai_task
                        GROUP BY task_type, status
                        """)
                .map((row, metadata) -> new TaskStatusCount(
                        row.get("task_type", String.class),
                        row.get("status", Integer.class),
                        row.get("count", Long.class)))
                .all();
    }

    public Mono<Long> sumConversationPendingCount() {
        return databaseClient.sql("SELECT COALESCE(SUM(pending_count), 0) AS pending FROM ai_conversation")
                .map((row, metadata) -> row.get("pending", Long.class))
                .one();
    }

    static MemoryExportRow mapExport(Row row) {
        return new MemoryExportRow(
                row.get("id", Long.class),
                row.get("record_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("user_id", String.class),
                row.get("scene_name", String.class),
                row.get("memory_type", String.class),
                row.get("priority", Integer.class),
                row.get("status", String.class),
                row.get("scope", String.class),
                row.get("version", Integer.class),
                row.get("decay_score", BigDecimal.class),
                row.get("last_accessed_at", Instant.class),
                row.get("created_at", Instant.class),
                row.get("content", String.class));
    }

    private static @Nullable OffsetDateTime offsetDateTime(@Nullable Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static MemoryRequestRecord mapRequest(Row row) {
        return new MemoryRequestRecord(
                row.get("id", Long.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("memory_level", String.class),
                row.get("memory_id", Long.class),
                row.get("request_type", String.class),
                row.get("reason", String.class),
                row.get("suggested_content", String.class),
                row.get("status", String.class),
                row.get("reviewed_by", String.class),
                row.get("reviewed_at", Instant.class),
                row.get("review_comment", String.class),
                row.get("created_at", Instant.class));
    }

    private static OrgMemoryRecord mapOrgMemory(Row row) {
        return new OrgMemoryRecord(
                row.get("id", Long.class),
                row.get("content", String.class),
                row.get("content_type", String.class),
                row.get("dedup_hash", String.class),
                row.get("trust_level", String.class),
                row.get("source_type", String.class),
                strings(row.get("source_team_ids", String[].class)),
                strings(row.get("source_user_ids", String[].class)),
                longs(row.get("source_memory_ids", Long[].class)),
                row.get("cross_vote_count", Integer.class),
                row.get("approved_by", String.class),
                row.get("approved_at", Instant.class),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private static List<String> strings(@Nullable String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    private static List<Long> longs(@Nullable Long[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    public record NewMemoryRequest(
            String userId,
            @Nullable String teamId,
            @Nullable String agentId,
            String memoryLevel,
            long memoryId,
            String requestType,
            String reason,
            @Nullable String suggestedContent) {}

    public record MemoryRequestRecord(
            Long id,
            String userId,
            @Nullable String teamId,
            @Nullable String agentId,
            String memoryLevel,
            long memoryId,
            String requestType,
            String reason,
            @Nullable String suggestedContent,
            String status,
            @Nullable String reviewedBy,
            @Nullable Instant reviewedAt,
            @Nullable String reviewComment,
            Instant createdAt) {}

    public record OrgMemoryRecord(
            Long id,
            String content,
            String contentType,
            String dedupHash,
            String trustLevel,
            String sourceType,
            List<String> sourceTeamIds,
            List<String> sourceUserIds,
            List<Long> sourceMemoryIds,
            int crossVoteCount,
            @Nullable String approvedBy,
            @Nullable Instant approvedAt,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    public record MemoryExportRow(
            long id,
            String recordId,
            String teamId,
            String agentId,
            String userId,
            @Nullable String sceneName,
            String memoryType,
            int priority,
            String status,
            String scope,
            int version,
            @Nullable BigDecimal decayScore,
            @Nullable Instant lastAccessedAt,
            @Nullable Instant createdAt,
            String content) {}

    public record TaskStatusCount(String taskType, int status, long count) {}
}
