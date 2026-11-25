package cn.tolim.pudans.infra.r2dbc.ai.catalog;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.infra.r2dbc.ai.AiJsonCodec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class CatalogEntryRepository {

    private static final String COLUMNS = """
            SELECT id, catalog_kind, entry_key, label, description, parent_key, sort_order,
                   enabled, metadata_jsonb::text AS metadata, valid_from, valid_to,
                   created_at, updated_at
              FROM catalog_entries
            """;

    private final DatabaseClient databaseClient;

    public Flux<CatalogEntry> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY catalog_kind, sort_order, entry_key")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<CatalogEntry> findAllByKind(String catalogKind) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE catalog_kind = :catalogKind
                         ORDER BY sort_order, entry_key
                        """)
                .bind("catalogKind", catalogKind)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<CatalogEntry> findActiveByKind(String catalogKind, Instant at) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE catalog_kind = :catalogKind
                           AND enabled = TRUE
                           AND (valid_from IS NULL OR valid_from <= :at)
                           AND (valid_to IS NULL OR valid_to > :at)
                         ORDER BY sort_order, entry_key
                        """)
                .bind("catalogKind", catalogKind)
                .bind("at", utc(at))
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<CatalogEntry> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<CatalogEntry> insert(CatalogEntry entry) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO catalog_entries (
                            id, catalog_kind, entry_key, label, description, parent_key,
                            sort_order, enabled, metadata_jsonb, valid_from, valid_to,
                            created_at, updated_at)
                        VALUES (
                            :id, :catalogKind, :entryKey, :label, :description, :parentKey,
                            :sortOrder, :enabled, CAST(:metadata AS jsonb), :validFrom, :validTo,
                            :createdAt, :updatedAt)
                        """)
                .bind("id", entry.id())
                .bind("catalogKind", entry.catalogKind())
                .bind("entryKey", entry.entryKey())
                .bind("label", entry.label());
        spec = bindNullable(spec, "description", entry.description(), String.class);
        spec = bindNullable(spec, "parentKey", entry.parentKey(), String.class);
        spec = spec.bind("sortOrder", entry.sortOrder())
                .bind("enabled", entry.enabled())
                .bind("metadata", AiJsonCodec.write(entry.metadata()));
        spec = bindNullable(spec, "validFrom", utc(entry.validFrom()), OffsetDateTime.class);
        spec = bindNullable(spec, "validTo", utc(entry.validTo()), OffsetDateTime.class);
        return duplicateAs(
                spec.bind("createdAt", utc(entry.createdAt()))
                        .bind("updatedAt", utc(entry.updatedAt()))
                        .then()
                        .thenReturn(entry),
                "catalog-entry-dup",
                "目录项类型与键已存在。");
    }

    public Mono<CatalogEntry> update(CatalogEntry entry) {
        var spec = databaseClient
                .sql("""
                        UPDATE catalog_entries
                           SET catalog_kind = :catalogKind,
                               entry_key = :entryKey,
                               label = :label,
                               description = :description,
                               parent_key = :parentKey,
                               sort_order = :sortOrder,
                               enabled = :enabled,
                               metadata_jsonb = CAST(:metadata AS jsonb),
                               valid_from = :validFrom,
                               valid_to = :validTo,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .bind("id", entry.id())
                .bind("catalogKind", entry.catalogKind())
                .bind("entryKey", entry.entryKey())
                .bind("label", entry.label());
        spec = bindNullable(spec, "description", entry.description(), String.class);
        spec = bindNullable(spec, "parentKey", entry.parentKey(), String.class);
        spec = spec.bind("sortOrder", entry.sortOrder())
                .bind("enabled", entry.enabled())
                .bind("metadata", AiJsonCodec.write(entry.metadata()));
        spec = bindNullable(spec, "validFrom", utc(entry.validFrom()), OffsetDateTime.class);
        spec = bindNullable(spec, "validTo", utc(entry.validTo()), OffsetDateTime.class);
        return duplicateAs(
                spec.bind("updatedAt", utc(entry.updatedAt()))
                        .then()
                        .thenReturn(entry),
                "catalog-entry-dup",
                "目录项类型与键已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient
                .sql("DELETE FROM catalog_entries WHERE id = :id")
                .bind("id", id)
                .then();
    }

    public Mono<Boolean> existsByKindAndKey(String catalogKind, String entryKey) {
        return databaseClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM catalog_entries
                             WHERE catalog_kind = :catalogKind AND entry_key = :entryKey)
                        """)
                .bind("catalogKind", catalogKind)
                .bind("entryKey", entryKey)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static CatalogEntry map(io.r2dbc.spi.Row row) {
        return new CatalogEntry(
                row.get("id", UUID.class),
                row.get("catalog_kind", String.class),
                row.get("entry_key", String.class),
                row.get("label", String.class),
                row.get("description", String.class),
                row.get("parent_key", String.class),
                row.get("sort_order", Integer.class),
                Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                AiJsonCodec.readObject(row.get("metadata", String.class)),
                instant(row.get("valid_from", OffsetDateTime.class)),
                instant(row.get("valid_to", OffsetDateTime.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
