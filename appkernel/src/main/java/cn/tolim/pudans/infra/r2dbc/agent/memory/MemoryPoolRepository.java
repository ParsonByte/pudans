package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.math.BigDecimal;
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
public class MemoryPoolRepository {

    private static final String COLUMNS = """
            SELECT id, scope, layer, memory_type, title, content, summary, generalized_content,
                   support_count, source_owner_count, source_workspace_count,
                   source_workspace_ids_jsonb::text AS source_workspace_ids,
                   evidence_summary_jsonb::text AS evidence_summary, safety_class, confidence,
                   status, visibility, valid_from, valid_to, content_hash, simhash, canonical_id,
                   superseded_by, reviewer_id, reviewed_at, next_review_at, created_at, updated_at
              FROM memory_pool_items
            """;

    private final DatabaseClient databaseClient;

    public Mono<MemoryPoolEntity> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemoryPoolEntity> listByStatus(String status, int limit) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE status = :status
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """)
                .bind("status", status)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<MemoryPoolEntity> publishedLexical(String query, int limit) {
        var pattern = "%" + query + "%";
        return databaseClient
                .sql(COLUMNS + """
                         WHERE status = 'published'
                           AND scope = 'global'
                           AND visibility = 'global'
                           AND safety_class = 'general'
                           AND (valid_to IS NULL OR valid_to > now())
                           AND (generalized_content ILIKE :pattern OR summary ILIKE :pattern
                                OR title ILIKE :pattern)
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """)
                .bind("pattern", pattern)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<MemoryPoolEntity> publishedByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        return databaseClient
                .sql(COLUMNS + """
                         WHERE id IN (:ids)
                           AND status = 'published'
                           AND scope = 'global'
                           AND visibility = 'global'
                           AND safety_class = 'general'
                           AND (valid_to IS NULL OR valid_to > now())
                        """)
                .bind("ids", ids)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<MemoryPoolEntity> findCurrentByHash(String contentHash) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE content_hash = :contentHash
                           AND status IN ('candidate', 'approved', 'published')
                         ORDER BY created_at DESC
                         LIMIT 1
                        """)
                .bind("contentHash", contentHash)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<MemoryPoolEntity> insert(MemoryPoolEntity entity) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO memory_pool_items (
                            id, scope, layer, memory_type, title, content, summary,
                            generalized_content, support_count, source_owner_count,
                            source_workspace_count, source_workspace_ids_jsonb,
                            evidence_summary_jsonb, safety_class, confidence, status, visibility,
                            valid_from, valid_to, content_hash, simhash, canonical_id,
                            superseded_by, reviewer_id, reviewed_at, next_review_at,
                            created_at, updated_at)
                        VALUES (
                            :id, :scope, :layer, :memoryType, :title, :content, :summary,
                            :generalizedContent, :supportCount, :sourceOwnerCount,
                            :sourceWorkspaceCount, CAST(:sourceWorkspaceIds AS jsonb),
                            CAST(:evidenceSummary AS jsonb), :safetyClass, :confidence, :status,
                            :visibility, :validFrom, :validTo, :contentHash, :simhash,
                            :canonicalId, :supersededBy, :reviewerId, :reviewedAt,
                            :nextReviewAt, :createdAt, :updatedAt)
                        """)
                .bind("id", entity.getId())
                .bind("scope", entity.getScope())
                .bind("layer", entity.getLayer())
                .bind("memoryType", entity.getMemoryType());
        spec = bindNullable(spec, "title", entity.getTitle(), String.class);
        spec = spec.bind("content", entity.getContent());
        spec = bindNullable(spec, "summary", entity.getSummary(), String.class);
        spec = bindNullable(
                spec, "generalizedContent", entity.getGeneralizedContent(), String.class);
        spec = spec.bind("supportCount", entity.getSupportCount())
                .bind("sourceOwnerCount", entity.getSourceOwnerCount())
                .bind("sourceWorkspaceCount", entity.getSourceWorkspaceCount())
                .bind("sourceWorkspaceIds", MemoryJsonCodec.write(entity.getSourceWorkspaceIds()))
                .bind("evidenceSummary", MemoryJsonCodec.write(entity.getEvidenceSummary()))
                .bind("safetyClass", entity.getSafetyClass())
                .bind("confidence", entity.getConfidence())
                .bind("status", entity.getStatus())
                .bind("visibility", entity.getVisibility())
                .bind("validFrom", utc(entity.getValidFrom()));
        spec = bindNullable(spec, "validTo", utc(entity.getValidTo()), OffsetDateTime.class);
        spec = bindNullable(spec, "contentHash", entity.getContentHash(), String.class);
        spec = bindNullable(spec, "simhash", entity.getSimhash(), Long.class);
        spec = bindNullable(spec, "canonicalId", entity.getCanonicalId(), UUID.class);
        spec = bindNullable(spec, "supersededBy", entity.getSupersededBy(), UUID.class);
        spec = bindNullable(spec, "reviewerId", entity.getReviewerId(), String.class);
        spec = bindNullable(spec, "reviewedAt", utc(entity.getReviewedAt()), OffsetDateTime.class);
        spec = bindNullable(
                spec, "nextReviewAt", utc(entity.getNextReviewAt()), OffsetDateTime.class);
        return duplicateAs(
                spec.bind("createdAt", utc(entity.getCreatedAt()))
                        .bind("updatedAt", utc(entity.getUpdatedAt()))
                        .then()
                        .thenReturn(entity),
                "memory-pool-conflict",
                "记忆池写入冲突。");
    }

    public Mono<MemoryPoolEntity> update(MemoryPoolEntity entity) {
        var spec = databaseClient
                .sql("""
                        UPDATE memory_pool_items
                           SET memory_type = :memoryType,
                               title = :title,
                               content = :content,
                               summary = :summary,
                               generalized_content = :generalizedContent,
                               support_count = :supportCount,
                               source_owner_count = :sourceOwnerCount,
                               source_workspace_count = :sourceWorkspaceCount,
                               source_workspace_ids_jsonb = CAST(:sourceWorkspaceIds AS jsonb),
                               evidence_summary_jsonb = CAST(:evidenceSummary AS jsonb),
                               safety_class = :safetyClass,
                               confidence = :confidence,
                               status = :status,
                               valid_from = :validFrom,
                               valid_to = :validTo,
                               content_hash = :contentHash,
                               simhash = :simhash,
                               canonical_id = :canonicalId,
                               superseded_by = :supersededBy,
                               reviewer_id = :reviewerId,
                               reviewed_at = :reviewedAt,
                               next_review_at = :nextReviewAt,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .bind("id", entity.getId())
                .bind("memoryType", entity.getMemoryType());
        spec = bindNullable(spec, "title", entity.getTitle(), String.class);
        spec = spec.bind("content", entity.getContent());
        spec = bindNullable(spec, "summary", entity.getSummary(), String.class);
        spec = bindNullable(
                spec, "generalizedContent", entity.getGeneralizedContent(), String.class);
        spec = spec.bind("supportCount", entity.getSupportCount())
                .bind("sourceOwnerCount", entity.getSourceOwnerCount())
                .bind("sourceWorkspaceCount", entity.getSourceWorkspaceCount())
                .bind("sourceWorkspaceIds", MemoryJsonCodec.write(entity.getSourceWorkspaceIds()))
                .bind("evidenceSummary", MemoryJsonCodec.write(entity.getEvidenceSummary()))
                .bind("safetyClass", entity.getSafetyClass())
                .bind("confidence", entity.getConfidence())
                .bind("status", entity.getStatus())
                .bind("validFrom", utc(entity.getValidFrom()));
        spec = bindNullable(spec, "validTo", utc(entity.getValidTo()), OffsetDateTime.class);
        spec = bindNullable(spec, "contentHash", entity.getContentHash(), String.class);
        spec = bindNullable(spec, "simhash", entity.getSimhash(), Long.class);
        spec = bindNullable(spec, "canonicalId", entity.getCanonicalId(), UUID.class);
        spec = bindNullable(spec, "supersededBy", entity.getSupersededBy(), UUID.class);
        spec = bindNullable(spec, "reviewerId", entity.getReviewerId(), String.class);
        spec = bindNullable(spec, "reviewedAt", utc(entity.getReviewedAt()), OffsetDateTime.class);
        spec = bindNullable(
                spec, "nextReviewAt", utc(entity.getNextReviewAt()), OffsetDateTime.class);
        return spec.bind("updatedAt", utc(entity.getUpdatedAt())).then().thenReturn(entity);
    }

    static MemoryPoolEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemoryPoolEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setScope(row.get("scope", String.class));
        entity.setLayer(row.get("layer", String.class));
        entity.setMemoryType(row.get("memory_type", String.class));
        entity.setTitle(row.get("title", String.class));
        entity.setContent(row.get("content", String.class));
        entity.setSummary(row.get("summary", String.class));
        entity.setGeneralizedContent(row.get("generalized_content", String.class));
        entity.setSupportCount(value(row.get("support_count", Integer.class)));
        entity.setSourceOwnerCount(value(row.get("source_owner_count", Integer.class)));
        entity.setSourceWorkspaceCount(value(row.get("source_workspace_count", Integer.class)));
        entity.setSourceWorkspaceIds(
                MemoryJsonCodec.readStrings(row.get("source_workspace_ids", String.class)));
        entity.setEvidenceSummary(
                MemoryJsonCodec.readObject(row.get("evidence_summary", String.class)));
        entity.setSafetyClass(row.get("safety_class", String.class));
        entity.setConfidence(row.get("confidence", BigDecimal.class));
        entity.setStatus(row.get("status", String.class));
        entity.setVisibility(row.get("visibility", String.class));
        entity.setValidFrom(instant(row.get("valid_from", OffsetDateTime.class)));
        entity.setValidTo(instant(row.get("valid_to", OffsetDateTime.class)));
        entity.setContentHash(row.get("content_hash", String.class));
        entity.setSimhash(row.get("simhash", Long.class));
        entity.setCanonicalId(row.get("canonical_id", UUID.class));
        entity.setSupersededBy(row.get("superseded_by", UUID.class));
        entity.setReviewerId(row.get("reviewer_id", String.class));
        entity.setReviewedAt(instant(row.get("reviewed_at", OffsetDateTime.class)));
        entity.setNextReviewAt(instant(row.get("next_review_at", OffsetDateTime.class)));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        entity.setUpdatedAt(instant(row.get("updated_at", OffsetDateTime.class)));
        return entity;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
