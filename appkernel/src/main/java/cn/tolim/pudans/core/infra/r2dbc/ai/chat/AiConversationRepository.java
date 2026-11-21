package cn.tolim.pudans.core.infra.r2dbc.ai.chat;

import io.r2dbc.spi.Row;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiConversationRepository {

    private final DatabaseClient databaseClient;

    public Mono<Conversation> create(NewConversation newConversation) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_conversation (
                            space_id, user_id, team_id, agent_id, agent_source, session_key,
                            task_id, title, mode_code, route_group
                        ) VALUES (
                            :spaceId, :userId, :teamId, :agentId, :agentSource, :sessionKey,
                            :taskId, :title, :modeCode, :routeGroup
                        )
                        ON CONFLICT (space_id, user_id, agent_source, session_key) DO NOTHING
                        RETURNING id, space_id, user_id, team_id, agent_id, agent_source, session_key,
                                  task_id, title, mode_code, route_group, status, l1_cursor_message_id,
                                  l2_cursor_l1_id, pending_count, pipeline_version, last_activity_at,
                                  created_at, updated_at
                        """)
                .bind("spaceId", newConversation.spaceId())
                .bind("userId", newConversation.userId())
                .bind("teamId", newConversation.teamId())
                .bind("agentId", newConversation.agentId())
                .bind("agentSource", newConversation.agentSource())
                .bind("sessionKey", newConversation.sessionKey());
        spec = AiR2dbcBindings.bindNullable(spec, "taskId", newConversation.taskId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "title", newConversation.title(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "modeCode", newConversation.modeCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "routeGroup", newConversation.routeGroup(), String.class);
        return spec.map((row, metadata) -> mapConversation(row)).one();
    }

    public Mono<Conversation> findBySession(
            String spaceId, String userId, String agentSource, String sessionKey) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, team_id, agent_id, agent_source, session_key,
                               task_id, title, mode_code, route_group, status, l1_cursor_message_id,
                               l2_cursor_l1_id, pending_count, pipeline_version, last_activity_at,
                               created_at, updated_at
                        FROM ai_conversation
                        WHERE space_id = :spaceId
                          AND user_id = :userId
                          AND agent_source = :agentSource
                          AND session_key = :sessionKey
                          AND deleted_at IS NULL
                        """)
                .bind("spaceId", spaceId)
                .bind("userId", userId)
                .bind("agentSource", agentSource)
                .bind("sessionKey", sessionKey)
                .map((row, metadata) -> mapConversation(row))
                .one();
    }

    public Mono<Conversation> findById(long id) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, team_id, agent_id, agent_source, session_key,
                               task_id, title, mode_code, route_group, status, l1_cursor_message_id,
                               l2_cursor_l1_id, pending_count, pipeline_version, last_activity_at,
                               created_at, updated_at
                        FROM ai_conversation
                        WHERE id = :id AND deleted_at IS NULL
                        """)
                .bind("id", id)
                .map((row, metadata) -> mapConversation(row))
                .one();
    }

    public Flux<Conversation> listByUser(String userId, long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, team_id, agent_id, agent_source, session_key,
                               task_id, title, mode_code, route_group, status, l1_cursor_message_id,
                               l2_cursor_l1_id, pending_count, pipeline_version, last_activity_at,
                               created_at, updated_at
                        FROM ai_conversation
                        WHERE user_id = :userId
                          AND deleted_at IS NULL
                          AND id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("userId", userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapConversation(row))
                .all();
    }

    public Mono<Boolean> softDelete(long id, String userId) {
        return databaseClient.sql("""
                        UPDATE ai_conversation
                        SET status = 'DELETED',
                            deleted_at = now(),
                            updated_at = now()
                        WHERE id = :id
                          AND user_id = :userId
                          AND deleted_at IS NULL
                        """)
                .bind("id", id)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Integer> addPending(long conversationId, int delta) {
        return databaseClient.sql("""
                        UPDATE ai_conversation
                        SET pending_count = pending_count + :delta,
                            last_activity_at = now(),
                            updated_at = now()
                        WHERE id = :conversationId AND deleted_at IS NULL
                        RETURNING pending_count
                        """)
                .bind("delta", delta)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> mapPendingCount(row))
                .one();
    }

    public Mono<Integer> reducePending(long conversationId, int delta) {
        return databaseClient.sql("""
                        UPDATE ai_conversation
                        SET pending_count = GREATEST(pending_count - :delta, 0),
                            updated_at = now()
                        WHERE id = :conversationId AND deleted_at IS NULL
                        RETURNING pending_count
                        """)
                .bind("delta", delta)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> mapPendingCount(row))
                .one();
    }

    public Mono<Boolean> advanceL1Cursor(long conversationId, long messageId) {
        return databaseClient.sql("""
                        UPDATE ai_conversation
                        SET l1_cursor_message_id = :messageId, updated_at = now()
                        WHERE id = :conversationId
                          AND deleted_at IS NULL
                          AND (l1_cursor_message_id IS NULL OR l1_cursor_message_id < :cursorLimit)
                        """)
                .bind("messageId", messageId)
                .bind("cursorLimit", messageId)
                .bind("conversationId", conversationId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Boolean> advanceL2Cursor(long conversationId, long l1Id) {
        return databaseClient.sql("""
                        UPDATE ai_conversation
                        SET l2_cursor_l1_id = :l1Id, updated_at = now()
                        WHERE id = :conversationId
                          AND deleted_at IS NULL
                          AND (l2_cursor_l1_id IS NULL OR l2_cursor_l1_id < :cursorLimit)
                        """)
                .bind("l1Id", l1Id)
                .bind("cursorLimit", l1Id)
                .bind("conversationId", conversationId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    static int mapPendingCount(Row row) {
        return row.get("pending_count", Integer.class);
    }

    static Conversation mapConversation(Row row) {
        return new Conversation(
                row.get("id", Long.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("agent_source", String.class),
                row.get("session_key", String.class),
                row.get("task_id", String.class),
                row.get("title", String.class),
                row.get("mode_code", String.class),
                row.get("route_group", String.class),
                row.get("status", String.class),
                row.get("l1_cursor_message_id", Long.class),
                row.get("l2_cursor_l1_id", Long.class),
                row.get("pending_count", Integer.class),
                row.get("pipeline_version", Long.class),
                row.get("last_activity_at", Instant.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    public record NewConversation(
            String spaceId,
            String userId,
            String teamId,
            String agentId,
            String agentSource,
            String sessionKey,
            @Nullable String taskId,
            @Nullable String title,
            @Nullable String modeCode,
            @Nullable String routeGroup) {

        public NewConversation {
            requireText(spaceId, "spaceId");
            requireText(userId, "userId");
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            requireText(agentSource, "agentSource");
            requireText(sessionKey, "sessionKey");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record Conversation(
            long id,
            String spaceId,
            String userId,
            String teamId,
            String agentId,
            String agentSource,
            String sessionKey,
            @Nullable String taskId,
            @Nullable String title,
            @Nullable String modeCode,
            @Nullable String routeGroup,
            String status,
            @Nullable Long l1CursorMessageId,
            @Nullable Long l2CursorL1Id,
            int pendingCount,
            long pipelineVersion,
            Instant lastActivityAt,
            Instant createdAt,
            Instant updatedAt) {}
}
