package cn.tolim.pudans.core.infra.r2dbc.ai.chat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
public class AiMessageRepository {

    private final DatabaseClient databaseClient;

    public Mono<Message> appendMessage(NewMessage newMessage) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_message (
                            conversation_id, space_id, user_id, team_id, agent_id, session_id,
                            task_id, request_id, turn_index, message_index, role, content,
                            content_type, token_count, model_code, metadata
                        ) VALUES (
                            :conversationId, :spaceId, :userId, :teamId, :agentId, :sessionId,
                            :taskId, :requestId, :turnIndex, :messageIndex, :role, :content,
                            :contentType, :tokenCount, :modelCode, CAST(:metadata AS jsonb)
                        )
                        RETURNING id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                                  task_id, request_id, turn_index, message_index, role, content,
                                  content_type, token_count, model_code, created_at
                        """)
                .bind("conversationId", newMessage.conversationId())
                .bind("spaceId", newMessage.spaceId())
                .bind("userId", newMessage.userId())
                .bind("teamId", newMessage.teamId())
                .bind("agentId", newMessage.agentId())
                .bind("turnIndex", newMessage.turnIndex())
                .bind("messageIndex", newMessage.messageIndex())
                .bind("role", newMessage.role())
                .bind("contentType", newMessage.contentType());
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", newMessage.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "taskId", newMessage.taskId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "requestId", newMessage.requestId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "content", newMessage.content(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "tokenCount", newMessage.tokenCount(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "modelCode", newMessage.modelCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "metadata",
                newMessage.metadata() == null ? "{}" : JsonUtils.objectToJson(newMessage.metadata()),
                String.class);
        return spec.map((row, metadata) -> mapMessage(row)).one();
    }

    public Flux<Message> listMessages(long conversationId, long afterId, int limit) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                               task_id, request_id, turn_index, message_index, role, content,
                               content_type, token_count, model_code, created_at
                        FROM ai_message
                        WHERE conversation_id = :conversationId
                          AND id > :afterId
                          AND deleted_at IS NULL
                        ORDER BY id
                        LIMIT :limit
                        """)
                .bind("conversationId", conversationId)
                .bind("afterId", afterId)
                .bind("limit", limit)
                .map((row, metadata) -> mapMessage(row))
                .all();
    }

    public Flux<Message> listRecentMessages(long conversationId, int limit) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                               task_id, request_id, turn_index, message_index, role, content,
                               content_type, token_count, model_code, created_at
                        FROM ai_message
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("conversationId", conversationId)
                .bind("limit", limit)
                .map((row, metadata) -> mapMessage(row))
                .all()
                .collectList()
                .flatMapMany(messages -> Flux.fromIterable(messages.reversed()));
    }

    public Flux<MessageMetadata> metadata(long conversationId) {
        return databaseClient.sql("""
                        SELECT id, metadata::text AS metadata
                        FROM ai_message
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                          AND metadata <> '{}'::jsonb
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> new MessageMetadata(
                        row.get("id", Long.class), json(row.get("metadata", String.class))))
                .all();
    }

    public Mono<Message> findLatest(long conversationId) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                               task_id, request_id, turn_index, message_index, role, content,
                               content_type, token_count, model_code, created_at
                        FROM ai_message
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> mapMessage(row))
                .one();
    }

    public Mono<Boolean> softDelete(long conversationId, long messageId) {
        return databaseClient.sql("""
                        UPDATE ai_message
                        SET deleted_at = now()
                        WHERE id = :messageId
                          AND conversation_id = :conversationId
                          AND deleted_at IS NULL
                        """)
                .bind("messageId", messageId)
                .bind("conversationId", conversationId)
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    public Mono<Long> findLatestId(long conversationId) {
        return databaseClient.sql("""
                        SELECT MAX(id) AS id
                        FROM ai_message
                        WHERE conversation_id = :conversationId AND deleted_at IS NULL
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .map(id -> id == null ? 0L : id)
                .defaultIfEmpty(0L);
    }

    public Flux<Message> searchByScope(
            String teamId, String userId, String agentId, String query, int limit) {
        return databaseClient.sql("""
                        SELECT id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                               task_id, request_id, turn_index, message_index, role, content,
                               content_type, token_count, model_code, created_at
                        FROM ai_message
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND agent_id = :agentId
                          AND deleted_at IS NULL
                          AND content ILIKE :pattern
                        ORDER BY similarity(content, :query) DESC, id DESC
                        LIMIT :limit
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .bind("pattern", "%" + query + "%")
                .bind("query", query)
                .bind("limit", limit)
                .map((row, metadata) -> mapMessage(row))
                .all();
    }

    private static JsonNode json(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        var text = value.toString();
        return text.isBlank() ? null : JsonUtils.jsonToObject(text, JsonNode.class);
    }

    static Message mapMessage(Row row) {
        return new Message(
                row.get("id", Long.class),
                row.get("conversation_id", Long.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_id", String.class),
                row.get("task_id", String.class),
                row.get("request_id", String.class),
                row.get("turn_index", Integer.class),
                row.get("message_index", Integer.class),
                row.get("role", String.class),
                row.get("content", String.class),
                row.get("content_type", String.class),
                row.get("token_count", Integer.class),
                row.get("model_code", String.class),
                row.get("created_at", Instant.class));
    }

    public record NewMessage(
            long conversationId,
            String spaceId,
            String userId,
            String teamId,
            String agentId,
            @Nullable String sessionId,
            @Nullable String taskId,
            @Nullable String requestId,
            int turnIndex,
            int messageIndex,
            String role,
            @Nullable String content,
            String contentType,
            @Nullable Integer tokenCount,
            @Nullable String modelCode,
            @Nullable JsonNode metadata) {

        public NewMessage {
            requireText(spaceId, "spaceId");
            requireText(userId, "userId");
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            requireText(role, "role");
            requireText(contentType, "contentType");
            if (turnIndex < 0) {
                throw new IllegalArgumentException("turnIndex 不能为负数");
            }
            if (messageIndex < 0) {
                throw new IllegalArgumentException("messageIndex 不能为负数");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record MessageMetadata(long messageId, @Nullable JsonNode metadata) {}

    public record Message(
            long id,
            long conversationId,
            String spaceId,
            String userId,
            String teamId,
            String agentId,
            @Nullable String sessionId,
            @Nullable String taskId,
            @Nullable String requestId,
            int turnIndex,
            int messageIndex,
            String role,
            @Nullable String content,
            String contentType,
            @Nullable Integer tokenCount,
            @Nullable String modelCode,
            Instant createdAt) {}
}
