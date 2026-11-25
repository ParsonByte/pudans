package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.CreationValues;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class InteractionRepository {

    public record InteractionCount(boolean active, long count) {}

    private static final String EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM work_interactions
                WHERE work_id = :workId AND owner_id = :ownerId AND interaction_type = :type
            ) AS active
            """;

    private static final String ADD_LIKE_SQL = """
            WITH changed AS (
                INSERT INTO work_interactions (
                    id, work_id, owner_id, workspace_id, interaction_type, metadata_jsonb, created_at)
                VALUES (
                    :id, :workId, :ownerId, :workspaceId, :interactionType,
                    CAST(:metadata AS jsonb), :createdAt)
                ON CONFLICT DO NOTHING
                RETURNING 1
            )
            UPDATE works
            SET like_count = like_count + (SELECT count(*) FROM changed),
                updated_at = :updatedAt
            WHERE id = :workId
            RETURNING like_count AS counter,
                ((SELECT count(*) FROM changed) > 0
                    OR EXISTS (
                        SELECT 1 FROM work_interactions
                        WHERE work_id = :workId AND owner_id = :ownerId
                            AND interaction_type = :interactionType)) AS active
            """;

    private static final String REMOVE_LIKE_SQL = """
            WITH changed AS (
                DELETE FROM work_interactions
                WHERE work_id = :workId AND owner_id = :ownerId
                    AND interaction_type = :interactionType
                RETURNING 1
            )
            UPDATE works
            SET like_count = GREATEST(like_count - (SELECT count(*) FROM changed), 0),
                updated_at = :updatedAt
            WHERE id = :workId
            RETURNING like_count AS counter, FALSE AS active
            """;

    private static final String ADD_FAVORITE_SQL = """
            WITH changed AS (
                INSERT INTO work_interactions (
                    id, work_id, owner_id, workspace_id, interaction_type, metadata_jsonb, created_at)
                VALUES (
                    :id, :workId, :ownerId, :workspaceId, :interactionType,
                    CAST(:metadata AS jsonb), :createdAt)
                ON CONFLICT DO NOTHING
                RETURNING 1
            )
            UPDATE works
            SET favorite_count = favorite_count + (SELECT count(*) FROM changed),
                updated_at = :updatedAt
            WHERE id = :workId
            RETURNING favorite_count AS counter,
                ((SELECT count(*) FROM changed) > 0
                    OR EXISTS (
                        SELECT 1 FROM work_interactions
                        WHERE work_id = :workId AND owner_id = :ownerId
                            AND interaction_type = :interactionType)) AS active
            """;

    private static final String REMOVE_FAVORITE_SQL = """
            WITH changed AS (
                DELETE FROM work_interactions
                WHERE work_id = :workId AND owner_id = :ownerId
                    AND interaction_type = :interactionType
                RETURNING 1
            )
            UPDATE works
            SET favorite_count = GREATEST(favorite_count - (SELECT count(*) FROM changed), 0),
                updated_at = :updatedAt
            WHERE id = :workId
            RETURNING favorite_count AS counter, FALSE AS active
            """;

    private static final String SHARE_SQL = """
            WITH changed AS (
                INSERT INTO work_interactions (
                    id, work_id, owner_id, workspace_id, interaction_type, metadata_jsonb, created_at)
                VALUES (
                    :id, :workId, :ownerId, :workspaceId, :interactionType,
                    CAST(:metadata AS jsonb), :createdAt)
                RETURNING 1
            )
            UPDATE works
            SET share_count = share_count + (SELECT count(*) FROM changed),
                updated_at = :updatedAt
            WHERE id = :workId
            RETURNING share_count AS counter
            """;

    private static final String REPORT_SQL = """
            INSERT INTO work_interactions (
                id, work_id, owner_id, workspace_id, interaction_type, metadata_jsonb, created_at)
            VALUES (
                :id, :workId, :ownerId, :workspaceId, :interactionType,
                CAST(:metadata AS jsonb), :createdAt)
            RETURNING id
            """;

    private static final String INCREMENT_REMIX_SQL = """
            UPDATE works
            SET remix_count = remix_count + 1
            WHERE id = :workId
            RETURNING remix_count AS counter
            """;

    private static final String INCREMENT_VIEW_SQL = """
            UPDATE works
            SET view_count = view_count + 1
            WHERE id = :workId
            RETURNING view_count AS counter
            """;

    private static final String FOLLOW_SQL = """
            INSERT INTO creator_follows (id, follower_id, creator_id, workspace_id, created_at)
            VALUES (:id, :followerId, :creatorId, :workspaceId, :createdAt)
            ON CONFLICT (follower_id, creator_id) DO NOTHING
            """;

    private static final String FOLLOW_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM creator_follows
                WHERE follower_id = :followerId AND creator_id = :creatorId
            ) AS following
            """;

    private static final String FOLLOWER_COUNT_SQL = """
            SELECT count(*) AS follower_count
            FROM creator_follows
            WHERE creator_id = :creatorId
            """;

    private final DatabaseClient databaseClient;

    public Mono<Boolean> exists(UUID workId, String ownerId, String interactionType) {
        return databaseClient
                .sql(EXISTS_SQL)
                .bind("workId", workId)
                .bind("ownerId", ownerId)
                .bind("type", interactionType)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("active", Boolean.class)))
                .one();
    }

    public Mono<InteractionCount> addLike(
            UUID workId, String ownerId, String workspaceId, Instant now) {
        return change(ADD_LIKE_SQL, workId, ownerId, workspaceId, CreationValues.INTERACTION_LIKE, now);
    }

    public Mono<InteractionCount> removeLike(
            UUID workId, String ownerId, String workspaceId, Instant now) {
        return change(REMOVE_LIKE_SQL, workId, ownerId, workspaceId, CreationValues.INTERACTION_LIKE, now);
    }

    public Mono<InteractionCount> addFavorite(
            UUID workId, String ownerId, String workspaceId, Instant now) {
        return change(
                ADD_FAVORITE_SQL, workId, ownerId, workspaceId, CreationValues.INTERACTION_FAVORITE, now);
    }

    public Mono<InteractionCount> removeFavorite(
            UUID workId, String ownerId, String workspaceId, Instant now) {
        return change(
                REMOVE_FAVORITE_SQL, workId, ownerId, workspaceId, CreationValues.INTERACTION_FAVORITE, now);
    }

    public Mono<Long> share(UUID workId, String ownerId, String workspaceId, Instant now) {
        return databaseClient
                .sql(SHARE_SQL)
                .bind("id", UUID.randomUUID())
                .bind("workId", workId)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("interactionType", CreationValues.INTERACTION_SHARE)
                .bind("metadata", "{}")
                .bind("createdAt", CreationRows.offset(now))
                .bind("updatedAt", CreationRows.offset(now))
                .map((row, metadata) -> row.get("counter", Long.class))
                .one();
    }

    public Mono<UUID> report(
            UUID workId, String ownerId, String workspaceId, Map<String, Object> metadata, Instant now) {
        return databaseClient
                .sql(REPORT_SQL)
                .bind("id", UUID.randomUUID())
                .bind("workId", workId)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("interactionType", CreationValues.INTERACTION_REPORT)
                .bind("metadata", CreationRows.jsonObject(metadata))
                .bind("createdAt", CreationRows.offset(now))
                .map((row, ignored) -> CreationRows.uuid(row, "id"))
                .one();
    }

    public Mono<Long> incrementRemix(UUID workId) {
        return databaseClient
                .sql(INCREMENT_REMIX_SQL)
                .bind("workId", workId)
                .map((row, metadata) -> row.get("counter", Long.class))
                .one();
    }

    public Mono<Long> incrementView(UUID workId) {
        return databaseClient
                .sql(INCREMENT_VIEW_SQL)
                .bind("workId", workId)
                .map((row, metadata) -> row.get("counter", Long.class))
                .one();
    }

    public Mono<Void> follow(
            String followerId, String creatorId, String workspaceId, Instant now) {
        return databaseClient
                .sql(FOLLOW_SQL)
                .bind("id", UUID.randomUUID())
                .bind("followerId", followerId)
                .bind("creatorId", creatorId)
                .bind("workspaceId", workspaceId)
                .bind("createdAt", CreationRows.offset(now))
                .then();
    }

    public Mono<Boolean> isFollowing(String followerId, String creatorId) {
        return databaseClient
                .sql(FOLLOW_EXISTS_SQL)
                .bind("followerId", followerId)
                .bind("creatorId", creatorId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("following", Boolean.class)))
                .one();
    }

    public Mono<Long> followerCount(String creatorId) {
        return databaseClient
                .sql(FOLLOWER_COUNT_SQL)
                .bind("creatorId", creatorId)
                .map((row, metadata) -> row.get("follower_count", Long.class))
                .one();
    }

    private Mono<InteractionCount> change(
            String sql,
            UUID workId,
            String ownerId,
            String workspaceId,
            String interactionType,
            Instant now) {
        return databaseClient
                .sql(sql)
                .bind("id", UUID.randomUUID())
                .bind("workId", workId)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("interactionType", interactionType)
                .bind("metadata", "{}")
                .bind("createdAt", CreationRows.offset(now))
                .bind("updatedAt", CreationRows.offset(now))
                .map((row, metadata) -> new InteractionCount(
                        Boolean.TRUE.equals(row.get("active", Boolean.class)),
                        row.get("counter", Long.class)))
                .one();
    }
}
