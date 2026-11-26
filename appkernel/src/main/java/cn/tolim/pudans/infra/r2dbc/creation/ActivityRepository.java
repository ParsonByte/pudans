package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.Activity;
import cn.tolim.pudans.contract.creation.CreationValues;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private static final String FIND_SQL = """
            SELECT id, owner_id, workspace_id, title, description, rules,
                cover_asset_id, template_id, status, visibility, starts_at,
                ends_at, created_at, updated_at
            FROM activities
            WHERE id = :id
            """;

    private static final String FEED_SQL = """
            SELECT id, owner_id, workspace_id, title, description, rules,
                cover_asset_id, template_id, status, visibility, starts_at,
                ends_at, created_at, updated_at
            FROM activities
            WHERE status = :publishedStatus
                AND visibility = :publicVisibility
                AND (CAST(:q AS varchar) IS NULL
                    OR COALESCE(title, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(description, '') ILIKE :q ESCAPE '!')
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (COALESCE(starts_at, created_at), id) <
                        (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY COALESCE(starts_at, created_at) DESC, id DESC
            LIMIT :fetchLimit
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE activities
               SET status = :status,
                   visibility = :visibility,
                   updated_at = :updatedAt
             WHERE id = :id
            RETURNING id, owner_id, workspace_id, title, description, rules,
                cover_asset_id, template_id, status, visibility, starts_at,
                ends_at, created_at, updated_at
            """;

    private static final String ALL_SQL = """
            SELECT id, owner_id, workspace_id, title, description, rules,
                cover_asset_id, template_id, status, visibility, starts_at,
                ends_at, created_at, updated_at
            FROM activities
            WHERE (CAST(:q AS varchar) IS NULL
                    OR COALESCE(title, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(description, '') ILIKE :q ESCAPE '!')
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (updated_at, id) < (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY updated_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private final DatabaseClient databaseClient;

    public Mono<Activity> findById(UUID id) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<Activity>> feed(
            String q, CreationCursor.Position cursor, int fetchLimit) {
        var spec = databaseClient
                .sql(FEED_SQL)
                .bind("publishedStatus", CreationValues.CONTENT_STATUS_PUBLISHED)
                .bind("publicVisibility", CreationValues.VISIBILITY_PUBLIC);
        if (CreationRows.likePattern(q) == null) {
            spec = spec.bindNull("q", String.class);
        } else {
            spec = spec.bind("q", CreationRows.likePattern(q));
        }
        if (cursor == null) {
            spec = spec.bindNull("cursorTime", OffsetDateTime.class).bindNull("cursorId", UUID.class);
        } else {
            spec = spec.bind("cursorTime", CreationRows.offset(cursor.sortTime()))
                    .bind("cursorId", cursor.id());
        }
        return spec.bind("fetchLimit", fetchLimit)
                .map((row, metadata) -> map(row))
                .all()
                .collectList();
    }

    public Mono<Activity> updateStatus(
            UUID id, String status, String visibility, Instant updatedAt) {
        return databaseClient
                .sql(UPDATE_STATUS_SQL)
                .bind("id", id)
                .bind("status", status)
                .bind("visibility", visibility)
                .bind("updatedAt", CreationRows.offset(updatedAt))
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<Activity>> allFeed(
            String q, CreationCursor.Position cursor, int fetchLimit) {
        var spec = databaseClient.sql(ALL_SQL);
        var pattern = CreationRows.likePattern(q);
        spec = pattern == null ? spec.bindNull("q", String.class) : spec.bind("q", pattern);
        if (cursor == null) {
            spec = spec.bindNull("cursorTime", OffsetDateTime.class).bindNull("cursorId", UUID.class);
        } else {
            spec = spec.bind("cursorTime", CreationRows.offset(cursor.sortTime()))
                    .bind("cursorId", cursor.id());
        }
        return spec.bind("fetchLimit", fetchLimit)
                .map((row, metadata) -> map(row))
                .all()
                .collectList();
    }

    private static Activity map(Row row) {
        return new Activity(
                CreationRows.uuid(row, "id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("title", String.class),
                row.get("description", String.class),
                row.get("rules", String.class),
                CreationRows.uuid(row, "cover_asset_id"),
                CreationRows.uuid(row, "template_id"),
                row.get("status", String.class),
                row.get("visibility", String.class),
                CreationRows.instant(row, "starts_at"),
                CreationRows.instant(row, "ends_at"),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }
}
