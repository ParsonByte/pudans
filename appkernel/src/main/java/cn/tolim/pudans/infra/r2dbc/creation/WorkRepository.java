package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Work;
import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class WorkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO works (
                id, owner_id, workspace_id, title, description, cover_asset_id,
                primary_asset_id, template_id, activity_id, remixed_from_work_id,
                tags_jsonb, metadata_jsonb, status, visibility, view_count,
                like_count, favorite_count, share_count, remix_count, published_at,
                created_at, updated_at)
            VALUES (
                :id, :ownerId, :workspaceId, :title, :description,
                CAST(:coverAssetId AS uuid), CAST(:primaryAssetId AS uuid),
                CAST(:templateId AS uuid), CAST(:activityId AS uuid),
                CAST(:remixedFromWorkId AS uuid), CAST(:tags AS jsonb),
                CAST(:metadata AS jsonb), :status, :visibility, :viewCount,
                :likeCount, :favoriteCount, :shareCount, :remixCount,
                CAST(:publishedAt AS timestamptz), :createdAt, :updatedAt)
            RETURNING id, owner_id, workspace_id, title, description, cover_asset_id,
                primary_asset_id, template_id, activity_id, remixed_from_work_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, view_count,
                like_count, favorite_count, share_count, remix_count, published_at,
                created_at, updated_at
            """;

    private static final String FIND_SQL = """
            SELECT id, owner_id, workspace_id, title, description, cover_asset_id,
                primary_asset_id, template_id, activity_id, remixed_from_work_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, view_count,
                like_count, favorite_count, share_count, remix_count, published_at,
                created_at, updated_at
            FROM works
            WHERE id = :id
            """;

    private static final String FEED_SQL = """
            SELECT id, owner_id, workspace_id, title, description, cover_asset_id,
                primary_asset_id, template_id, activity_id, remixed_from_work_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, view_count,
                like_count, favorite_count, share_count, remix_count, published_at,
                created_at, updated_at
            FROM works
            WHERE status = :publishedStatus
                AND visibility = :publicVisibility
                AND published_at IS NOT NULL
                AND (CAST(:q AS varchar) IS NULL
                    OR COALESCE(title, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(description, '') ILIKE :q ESCAPE '!')
                AND (CAST(:templateId AS uuid) IS NULL OR template_id = :templateId)
                AND (CAST(:activityId AS uuid) IS NULL OR activity_id = :activityId)
                AND (CAST(:creator AS varchar) IS NULL OR owner_id = :creator)
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (published_at, id) < (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY published_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private static final String RELATED_SQL = """
            SELECT id, owner_id, workspace_id, title, description, cover_asset_id,
                primary_asset_id, template_id, activity_id, remixed_from_work_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, view_count,
                like_count, favorite_count, share_count, remix_count, published_at,
                created_at, updated_at
            FROM works
            WHERE id <> :sourceId
                AND status = :publishedStatus
                AND visibility = :publicVisibility
                AND published_at IS NOT NULL
                AND (
                    owner_id = :ownerId
                    OR (CAST(:templateId AS uuid) IS NOT NULL AND template_id = :templateId)
                    OR (CAST(:activityId AS uuid) IS NOT NULL AND activity_id = :activityId)
                )
            ORDER BY published_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private final DatabaseClient databaseClient;

    public Mono<Work> insert(Work work) {
        var spec = databaseClient
                .sql(INSERT_SQL)
                .bind("id", work.id())
                .bind("ownerId", work.ownerId())
                .bind("workspaceId", work.workspaceId())
                .bind("title", work.title());
        spec = bindNullable(spec, "description", work.description(), String.class);
        spec = bindNullable(spec, "coverAssetId", work.coverAssetId(), UUID.class);
        spec = bindNullable(spec, "primaryAssetId", work.primaryAssetId(), UUID.class);
        spec = bindNullable(spec, "templateId", work.templateId(), UUID.class);
        spec = bindNullable(spec, "activityId", work.activityId(), UUID.class);
        spec = bindNullable(spec, "remixedFromWorkId", work.remixedFromWorkId(), UUID.class);
        spec = spec.bind("tags", CreationRows.jsonList(work.tags()));
        spec = spec.bind("metadata", CreationRows.jsonObject(work.metadata()));
        spec = spec.bind("status", work.status())
                .bind("visibility", work.visibility())
                .bind("viewCount", work.viewCount())
                .bind("likeCount", work.likeCount())
                .bind("favoriteCount", work.favoriteCount())
                .bind("shareCount", work.shareCount())
                .bind("remixCount", work.remixCount());
        spec = bindNullable(spec, "publishedAt", CreationRows.offset(work.publishedAt()), OffsetDateTime.class);
        spec = spec.bind("createdAt", CreationRows.offset(work.createdAt()))
                .bind("updatedAt", CreationRows.offset(work.updatedAt()));
        return spec.map((row, metadata) -> map(row)).one();
    }

    public Mono<Work> findById(UUID id) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<Work>> feed(
            String q,
            UUID templateId,
            UUID activityId,
            String creator,
            CreationCursor.Position cursor,
            int fetchLimit) {
        var spec = databaseClient
                .sql(FEED_SQL)
                .bind("publishedStatus", CreationValues.CONTENT_STATUS_PUBLISHED)
                .bind("publicVisibility", CreationValues.VISIBILITY_PUBLIC);
        spec = bindNullable(spec, "q", CreationRows.likePattern(q), String.class);
        spec = bindNullable(spec, "templateId", templateId, UUID.class);
        spec = bindNullable(spec, "activityId", activityId, UUID.class);
        spec = bindNullable(
                spec, "creator", StringUtils.hasText(creator) ? creator.trim() : null, String.class);
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

    public Mono<List<Work>> related(Work source, int fetchLimit) {
        var spec = databaseClient
                .sql(RELATED_SQL)
                .bind("sourceId", source.id())
                .bind("ownerId", source.ownerId())
                .bind("publishedStatus", CreationValues.CONTENT_STATUS_PUBLISHED)
                .bind("publicVisibility", CreationValues.VISIBILITY_PUBLIC);
        spec = bindNullable(spec, "templateId", source.templateId(), UUID.class);
        spec = bindNullable(spec, "activityId", source.activityId(), UUID.class);
        return spec.bind("fetchLimit", fetchLimit)
                .map((row, metadata) -> map(row))
                .all()
                .collectList();
    }

    private static Work map(Row row) {
        return new Work(
                CreationRows.uuid(row, "id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("title", String.class),
                row.get("description", String.class),
                CreationRows.uuid(row, "cover_asset_id"),
                CreationRows.uuid(row, "primary_asset_id"),
                CreationRows.uuid(row, "template_id"),
                CreationRows.uuid(row, "activity_id"),
                CreationRows.uuid(row, "remixed_from_work_id"),
                CreationRows.stringList(row.get("tags_json", String.class)),
                CreationRows.objectMap(row.get("metadata_json", String.class)),
                row.get("status", String.class),
                row.get("visibility", String.class),
                row.get("view_count", Long.class),
                row.get("like_count", Long.class),
                row.get("favorite_count", Long.class),
                row.get("share_count", Long.class),
                row.get("remix_count", Long.class),
                CreationRows.instant(row, "published_at"),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
