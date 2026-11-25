package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.dto.AssetFacetBucket;
import cn.tolim.pudans.infra.utils.RequestIdentity;
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
public class AssetRepository {

    private static final String INSERT_SQL = """
            INSERT INTO assets (
                id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb, content_type, size, width, height, duration_ms,
                tags_jsonb, metadata_jsonb, status, created_at, updated_at)
            VALUES (
                :id, :ownerId, :workspaceId, :kind, :source, :storageFileId,
                CAST(:generationTaskId AS uuid), CAST(:parentAssetId AS uuid),
                :name, :description, :prompt, CAST(:settings AS jsonb),
                :contentType, :size, :width, :height, :durationMs,
                CAST(:tags AS jsonb), CAST(:metadata AS jsonb), :status,
                :createdAt, :updatedAt)
            RETURNING id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb::text AS settings_json, content_type, size, width, height,
                duration_ms, tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, created_at, updated_at
            """;

    private static final String FIND_OWNED_SQL = """
            SELECT id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb::text AS settings_json, content_type, size, width, height,
                duration_ms, tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, created_at, updated_at
            FROM assets
            WHERE id = :id AND owner_id = :ownerId AND workspace_id = :workspaceId
                AND status = :activeStatus
            """;

    private static final String FIND_SQL = """
            SELECT id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb::text AS settings_json, content_type, size, width, height,
                duration_ms, tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, created_at, updated_at
            FROM assets
            WHERE id = :id AND status = :activeStatus
            """;

    private static final String FIND_BY_STORAGE_FILE_SQL = """
            SELECT id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb::text AS settings_json, content_type, size, width, height,
                duration_ms, tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, created_at, updated_at
            FROM assets
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND storage_file_id = :storageFileId
                AND generation_task_id IS NOT DISTINCT FROM CAST(:generationTaskId AS uuid)
                AND status = :activeStatus
            ORDER BY created_at ASC
            LIMIT 1
            """;

    private static final String LIST_SQL = """
            SELECT id, owner_id, workspace_id, kind, source, storage_file_id,
                generation_task_id, parent_asset_id, name, description, prompt,
                settings_jsonb::text AS settings_json, content_type, size, width, height,
                duration_ms, tags_jsonb::text AS tags_json, metadata_jsonb::text AS metadata_json,
                status, created_at, updated_at
            FROM assets
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND status = :activeStatus
                AND (CAST(:kind AS varchar) IS NULL OR kind = :kind)
                AND (CAST(:source AS varchar) IS NULL OR source = :source)
                AND (CAST(:q AS varchar) IS NULL
                    OR COALESCE(name, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(prompt, '') ILIKE :q ESCAPE '!'
                    OR COALESCE(description, '') ILIKE :q ESCAPE '!')
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (updated_at, id) < (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY updated_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private static final String KIND_FACETS_SQL = """
            SELECT kind AS facet_key, count(*) AS facet_count
            FROM assets
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND status = :activeStatus
            GROUP BY kind
            ORDER BY kind
            """;

    private static final String SOURCE_FACETS_SQL = """
            SELECT source AS facet_key, count(*) AS facet_count
            FROM assets
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND status = :activeStatus
            GROUP BY source
            ORDER BY source
            """;

    private static final String COUNT_SQL = """
            SELECT count(*) AS facet_count
            FROM assets
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND status = :activeStatus
            """;

    private final DatabaseClient databaseClient;

    public Mono<Asset> insert(Asset asset) {
        var spec = databaseClient
                .sql(INSERT_SQL)
                .bind("id", asset.id())
                .bind("ownerId", asset.ownerId())
                .bind("workspaceId", asset.workspaceId())
                .bind("kind", asset.kind())
                .bind("source", asset.source());
        spec = bindNullable(spec, "storageFileId", asset.storageFileId(), String.class);
        spec = bindNullable(spec, "generationTaskId", asset.generationTaskId(), UUID.class);
        spec = bindNullable(spec, "parentAssetId", asset.parentAssetId(), UUID.class);
        spec = bindNullable(spec, "name", asset.name(), String.class);
        spec = bindNullable(spec, "description", asset.description(), String.class);
        spec = bindNullable(spec, "prompt", asset.prompt(), String.class);
        spec = spec.bind("settings", CreationRows.jsonObject(asset.settings()));
        spec = bindNullable(spec, "contentType", asset.contentType(), String.class);
        spec = bindNullable(spec, "size", asset.size(), Long.class);
        spec = bindNullable(spec, "width", asset.width(), Integer.class);
        spec = bindNullable(spec, "height", asset.height(), Integer.class);
        spec = bindNullable(spec, "durationMs", asset.durationMs(), Long.class);
        spec = spec.bind("tags", CreationRows.jsonList(asset.tags()));
        spec = spec.bind("metadata", CreationRows.jsonObject(asset.metadata()));
        spec = spec.bind("status", asset.status());
        spec = spec.bind("createdAt", CreationRows.offset(asset.createdAt()));
        spec = spec.bind("updatedAt", CreationRows.offset(asset.updatedAt()));
        return spec.map((row, metadata) -> map(row)).one();
    }

    public Mono<Asset> findOwned(UUID id, RequestIdentity identity) {
        return databaseClient
                .sql(FIND_OWNED_SQL)
                .bind("id", id)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Asset> findById(UUID id) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("id", id)
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Asset> findByStorageFile(
            String storageFileId, UUID generationTaskId, RequestIdentity identity) {
        var spec = databaseClient
                .sql(FIND_BY_STORAGE_FILE_SQL)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("storageFileId", storageFileId)
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE);
        spec = bindNullable(spec, "generationTaskId", generationTaskId, UUID.class);
        return spec.map((row, metadata) -> map(row)).one();
    }

    public Mono<List<Asset>> list(
            RequestIdentity identity,
            String kind,
            String source,
            String q,
            CreationCursor.Position cursor,
            int fetchLimit) {
        var spec = databaseClient
                .sql(LIST_SQL)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE);
        spec = bindNullable(spec, "kind", StringUtils.hasText(kind) ? kind.trim() : null, String.class);
        spec = bindNullable(
                spec, "source", StringUtils.hasText(source) ? source.trim() : null, String.class);
        spec = bindNullable(spec, "q", CreationRows.likePattern(q), String.class);
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

    public Mono<List<AssetFacetBucket>> kindFacets(RequestIdentity identity) {
        return databaseClient
                .sql(KIND_FACETS_SQL)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE)
                .map((row, metadata) -> facet(row))
                .all()
                .collectList();
    }

    public Mono<List<AssetFacetBucket>> sourceFacets(RequestIdentity identity) {
        return databaseClient
                .sql(SOURCE_FACETS_SQL)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE)
                .map((row, metadata) -> facet(row))
                .all()
                .collectList();
    }

    public Mono<Long> count(RequestIdentity identity) {
        return databaseClient
                .sql(COUNT_SQL)
                .bind("ownerId", identity.ownerId())
                .bind("workspaceId", identity.workspaceId())
                .bind("activeStatus", CreationValues.ASSET_STATUS_ACTIVE)
                .map((row, metadata) -> row.get("facet_count", Long.class))
                .one();
    }

    private static Asset map(Row row) {
        return new Asset(
                CreationRows.uuid(row, "id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("kind", String.class),
                row.get("source", String.class),
                row.get("storage_file_id", String.class),
                CreationRows.uuid(row, "generation_task_id"),
                CreationRows.uuid(row, "parent_asset_id"),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("prompt", String.class),
                CreationRows.objectMap(row.get("settings_json", String.class)),
                row.get("content_type", String.class),
                CreationRows.longValue(row, "size"),
                CreationRows.integer(row, "width"),
                CreationRows.integer(row, "height"),
                CreationRows.longValue(row, "duration_ms"),
                CreationRows.stringList(row.get("tags_json", String.class)),
                CreationRows.objectMap(row.get("metadata_json", String.class)),
                row.get("status", String.class),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }

    private static AssetFacetBucket facet(Row row) {
        return new AssetFacetBucket(
                row.get("facet_key", String.class),
                row.get("facet_count", Long.class));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
