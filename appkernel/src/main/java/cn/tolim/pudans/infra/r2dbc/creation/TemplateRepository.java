package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Template;
import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class TemplateRepository {

    private static final String FIND_SQL = """
            SELECT id, owner_id, workspace_id, name, title, description, media_type,
                prompt, settings_jsonb::text AS settings_json,
                parameter_schema_jsonb::text AS parameter_schema_json, cover_asset_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, published_at,
                created_at, updated_at
            FROM creation_templates
            WHERE id = :id
            """;

    private static final String FEED_SQL = """
            SELECT id, owner_id, workspace_id, name, title, description, media_type,
                prompt, settings_jsonb::text AS settings_json,
                parameter_schema_jsonb::text AS parameter_schema_json, cover_asset_id,
                tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, visibility, published_at,
                created_at, updated_at
            FROM creation_templates
            WHERE status = :publishedStatus
                AND visibility = :publicVisibility
                AND published_at IS NOT NULL
                AND (CAST(:q AS varchar) IS NULL
                    OR COALESCE(title, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(description, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(prompt, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(name, '') ILIKE :q ESCAPE '!')
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (published_at, id) < (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY published_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private final DatabaseClient databaseClient;

    public Mono<Template> findById(UUID id) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<Template>> feed(
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

    private static Template map(Row row) {
        return new Template(
                CreationRows.uuid(row, "id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("name", String.class),
                row.get("title", String.class),
                row.get("description", String.class),
                row.get("media_type", String.class),
                row.get("prompt", String.class),
                CreationRows.objectMap(row.get("settings_json", String.class)),
                CreationRows.objectMap(row.get("parameter_schema_json", String.class)),
                CreationRows.uuid(row, "cover_asset_id"),
                CreationRows.stringList(row.get("tags_json", String.class)),
                CreationRows.objectMap(row.get("metadata_json", String.class)),
                row.get("status", String.class),
                row.get("visibility", String.class),
                CreationRows.instant(row, "published_at"),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }
}
