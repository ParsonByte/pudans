package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.Subject;
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
public class SubjectRepository {

    private static final String FIND_SQL = """
            SELECT id, owner_id, workspace_id, name, description, kind,
                avatar_asset_id, tags_jsonb::text AS tags_json,
                metadata_jsonb::text AS metadata_json, status, visibility,
                created_at, updated_at
            FROM subjects
            WHERE id = :id
            """;

    private static final String LIST_MINE_SQL = """
            SELECT id, owner_id, workspace_id, name, description, kind,
                avatar_asset_id, tags_jsonb::text AS tags_json,
                metadata_jsonb::text AS metadata_json, status, visibility,
                created_at, updated_at
            FROM subjects
            WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                AND (CAST(:cursorTime AS timestamptz) IS NULL
                    OR (updated_at, id) < (CAST(:cursorTime AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY updated_at DESC, id DESC
            LIMIT :fetchLimit
            """;

    private final DatabaseClient databaseClient;

    public Mono<Subject> findById(UUID id) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<Subject>> listMine(
            String ownerId,
            String workspaceId,
            CreationCursor.Position cursor,
            int fetchLimit) {
        var spec = databaseClient
                .sql(LIST_MINE_SQL)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId);
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

    private static Subject map(Row row) {
        return new Subject(
                CreationRows.uuid(row, "id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("kind", String.class),
                CreationRows.uuid(row, "avatar_asset_id"),
                CreationRows.stringList(row.get("tags_json", String.class)),
                CreationRows.objectMap(row.get("metadata_json", String.class)),
                row.get("status", String.class),
                row.get("visibility", String.class),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }
}
