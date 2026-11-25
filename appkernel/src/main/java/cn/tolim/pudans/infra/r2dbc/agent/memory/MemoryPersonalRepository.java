package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryPersonalRepository {

    private static final String COLUMNS = """
            SELECT id, owner_id, workspace_id, layer, memory_type, canonical_key, content,
                   summary, evidence_jsonb::text AS evidence, source_ids_jsonb::text AS source_ids,
                   confidence, status, valid_from, valid_to, content_hash, simhash,
                   canonical_id, superseded_by, created_at, updated_at
              FROM personal_memory_items
            """;

    private final DatabaseClient databaseClient;

    public record MemoryTypeStat(
            String memoryType, int memoryCount, BigDecimal averageConfidence, Instant lastUpdatedAt) {}

    public Mono<MemoryPersonalEntity> findOwned(
            UUID id, String ownerId, String workspaceId) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE id = :id AND owner_id = :ownerId AND workspace_id = :workspaceId
                        """)
                .bind("id", id)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemoryPersonalEntity> listOwned(
            String ownerId, String workspaceId, String layer, String status, int limit) {
        var spec = databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND (:layer IS NULL OR layer = :layer)
                           AND (:status IS NULL OR status = :status)
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId);
        spec = bindNullable(spec, "layer", layer, String.class);
        spec = bindNullable(spec, "status", status, String.class);
        return spec.bind("limit", limit).map((row, metadata) -> map(row)).all();
    }

    public Flux<MemoryTypeStat> statsOwned(String ownerId, String workspaceId) {
        return databaseClient
                .sql("""
                        SELECT memory_type,
                               count(*) AS memory_count,
                               avg(confidence) AS average_confidence,
                               max(updated_at) AS last_updated_at
                          FROM personal_memory_items
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND status = 'active'
                           AND layer IN ('L1', 'L2', 'L3')
                           AND (valid_to IS NULL OR valid_to > now())
                         GROUP BY memory_type
                         ORDER BY memory_type
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> new MemoryTypeStat(
                        row.get("memory_type", String.class),
                        intValue(row.get("memory_count", Long.class)),
                        row.get("average_confidence", BigDecimal.class),
                        instant(row.get("last_updated_at", OffsetDateTime.class))))
                .all();
    }

    public Mono<MemoryPersonalEntity> findActiveByHash(
            String ownerId, String workspaceId, String layer, String contentHash) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND layer = :layer
                           AND content_hash = :contentHash
                           AND status = 'active'
                           AND (valid_to IS NULL OR valid_to > now())
                         ORDER BY created_at DESC
                         LIMIT 1
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("layer", layer)
                .bind("contentHash", contentHash)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<MemoryPersonalEntity> findActiveByCanonicalKey(
            String ownerId, String workspaceId, String canonicalKey) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND canonical_key = :canonicalKey
                           AND status = 'active'
                           AND (valid_to IS NULL OR valid_to > now())
                         ORDER BY updated_at DESC
                         LIMIT 1
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("canonicalKey", canonicalKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemoryPersonalEntity> activeLexical(
            String ownerId, String workspaceId, String query, int limit) {
        var pattern = "%" + query + "%";
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND status = 'active'
                           AND layer IN ('L1', 'L2', 'L3')
                           AND (valid_to IS NULL OR valid_to > now())
                           AND (content ILIKE :pattern OR summary ILIKE :pattern)
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("pattern", pattern)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<MemoryPersonalEntity> activeByIds(
            String ownerId, String workspaceId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND id IN (:ids)
                           AND status = 'active'
                           AND layer IN ('L1', 'L2', 'L3')
                           AND (valid_to IS NULL OR valid_to > now())
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("ids", ids)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<MemoryPersonalEntity> activeForPooling(int limit) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE status = 'active'
                           AND layer IN ('L1', 'L2', 'L3')
                           AND memory_type IN ('fact', 'project', 'subject', 'creation')
                           AND confidence >= 0.6
                           AND canonical_key IS NOT NULL
                           AND (valid_to IS NULL OR valid_to > now())
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<MemoryPersonalEntity> insert(MemoryPersonalEntity entity) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO personal_memory_items (
                            id, owner_id, workspace_id, layer, memory_type, canonical_key,
                            content, summary, evidence_jsonb, source_ids_jsonb, confidence,
                            status, valid_from, valid_to, content_hash, simhash, canonical_id,
                            superseded_by, created_at, updated_at)
                        VALUES (
                            :id, :ownerId, :workspaceId, :layer, :memoryType, :canonicalKey,
                            :content, :summary, CAST(:evidence AS jsonb),
                            CAST(:sourceIds AS jsonb), :confidence, :status, :validFrom,
                            :validTo, :contentHash, :simhash, :canonicalId, :supersededBy,
                            :createdAt, :updatedAt)
                        """)
                .bind("id", entity.getId())
                .bind("ownerId", entity.getOwnerId())
                .bind("workspaceId", entity.getWorkspaceId())
                .bind("layer", entity.getLayer())
                .bind("memoryType", entity.getMemoryType());
        spec = bindNullable(spec, "canonicalKey", entity.getCanonicalKey(), String.class);
        spec = spec.bind("content", entity.getContent())
                .bind("summary", entity.getSummary())
                .bind("evidence", MemoryJsonCodec.write(entity.getEvidence()))
                .bind("sourceIds", MemoryJsonCodec.write(entity.getSourceIds()))
                .bind("confidence", entity.getConfidence())
                .bind("status", entity.getStatus())
                .bind("validFrom", utc(entity.getValidFrom()));
        spec = bindNullable(spec, "validTo", utc(entity.getValidTo()), OffsetDateTime.class);
        spec = bindNullable(spec, "contentHash", entity.getContentHash(), String.class);
        spec = bindNullable(spec, "simhash", entity.getSimhash(), Long.class);
        spec = bindNullable(spec, "canonicalId", entity.getCanonicalId(), UUID.class);
        spec = bindNullable(spec, "supersededBy", entity.getSupersededBy(), UUID.class);
        return duplicateAs(
                spec.bind("createdAt", utc(entity.getCreatedAt()))
                        .bind("updatedAt", utc(entity.getUpdatedAt()))
                        .then()
                        .thenReturn(entity),
                "memory-item-conflict",
                "个人记忆写入冲突。");
    }

    public Mono<MemoryPersonalEntity> update(MemoryPersonalEntity entity) {
        var spec = databaseClient
                .sql("""
                        UPDATE personal_memory_items
                           SET memory_type = :memoryType,
                               canonical_key = :canonicalKey,
                               content = :content,
                               summary = :summary,
                               evidence_jsonb = CAST(:evidence AS jsonb),
                               source_ids_jsonb = CAST(:sourceIds AS jsonb),
                               confidence = :confidence,
                               status = :status,
                               valid_from = :validFrom,
                               valid_to = :validTo,
                               content_hash = :contentHash,
                               simhash = :simhash,
                               canonical_id = :canonicalId,
                               superseded_by = :supersededBy,
                               updated_at = :updatedAt
                         WHERE id = :id
                           AND owner_id = :ownerId
                           AND workspace_id = :workspaceId
                        """)
                .bind("id", entity.getId())
                .bind("ownerId", entity.getOwnerId())
                .bind("workspaceId", entity.getWorkspaceId())
                .bind("memoryType", entity.getMemoryType());
        var withCanonicalKey = bindNullable(
                spec, "canonicalKey", entity.getCanonicalKey(), String.class);
        var withContent = withCanonicalKey
                .bind("content", entity.getContent())
                .bind("summary", entity.getSummary())
                .bind("evidence", MemoryJsonCodec.write(entity.getEvidence()))
                .bind("sourceIds", MemoryJsonCodec.write(entity.getSourceIds()))
                .bind("confidence", entity.getConfidence())
                .bind("status", entity.getStatus())
                .bind("validFrom", utc(entity.getValidFrom()));
        var withValidity = bindNullable(
                withContent, "validTo", utc(entity.getValidTo()), OffsetDateTime.class);
        var withHash = bindNullable(
                bindNullable(
                        withValidity,
                        "contentHash",
                        entity.getContentHash(),
                        String.class),
                "simhash",
                entity.getSimhash(),
                Long.class);
        var withCanonical = bindNullable(
                withHash, "canonicalId", entity.getCanonicalId(), UUID.class);
        return bindNullable(
                        withCanonical, "supersededBy", entity.getSupersededBy(), UUID.class)
                .bind("updatedAt", utc(entity.getUpdatedAt()))
                .then()
                .thenReturn(entity);
    }

    static MemoryPersonalEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemoryPersonalEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setOwnerId(row.get("owner_id", String.class));
        entity.setWorkspaceId(row.get("workspace_id", String.class));
        entity.setLayer(row.get("layer", String.class));
        entity.setMemoryType(row.get("memory_type", String.class));
        entity.setCanonicalKey(row.get("canonical_key", String.class));
        entity.setContent(row.get("content", String.class));
        entity.setSummary(row.get("summary", String.class));
        entity.setEvidence(MemoryJsonCodec.readObjectList(row.get("evidence", String.class)));
        entity.setSourceIds(MemoryJsonCodec.readStrings(row.get("source_ids", String.class)));
        entity.setConfidence(row.get("confidence", BigDecimal.class));
        entity.setStatus(row.get("status", String.class));
        entity.setValidFrom(instant(row.get("valid_from", OffsetDateTime.class)));
        entity.setValidTo(instant(row.get("valid_to", OffsetDateTime.class)));
        entity.setContentHash(row.get("content_hash", String.class));
        entity.setSimhash(row.get("simhash", Long.class));
        entity.setCanonicalId(row.get("canonical_id", UUID.class));
        entity.setSupersededBy(row.get("superseded_by", UUID.class));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        entity.setUpdatedAt(instant(row.get("updated_at", OffsetDateTime.class)));
        return entity;
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
