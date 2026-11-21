package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiOrgMemoryRepository {

    private static final String TRUST_PENDING = "PENDING";

    private static final String TRUST_VERIFIED = "VERIFIED";

    private static final String TRUST_REVOKED = "REVOKED";

    private static final String ORG_COLUMNS =
            "id, content, content_type, dedup_hash, trust_level, source_type, source_team_ids, "
                    + "source_user_ids, source_memory_ids, cross_vote_count, approved_by, approved_at, "
                    + "is_active, created_at, updated_at";

    private final DatabaseClient databaseClient;

    private final AiMemoryGovernanceRepository governanceRepository;

    public Flux<OrgMemoryRecord> listPending(long beforeId, int limit) {
        return governanceRepository.listOrgMemories(TRUST_PENDING, beforeId, limit);
    }

    public Flux<OrgMemoryRecord> listApproved(long beforeId, int limit) {
        return governanceRepository.listOrgMemories(TRUST_VERIFIED, beforeId, limit);
    }

    public Flux<OrgMemoryRecord> listRevoked(long beforeId, int limit) {
        // 撤销记录为停用状态，治理仓库的列表只返回活跃记录。
        return databaseClient.sql("SELECT " + ORG_COLUMNS + """
                         FROM ai_org_memory
                         WHERE trust_level = :trustLevel
                           AND id < :beforeId
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("trustLevel", TRUST_REVOKED)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapOrgMemory(row))
                .all();
    }

    // 聊天召回只使用已核验且被当前团队来源覆盖的组织记忆
    public Flux<OrgMemoryRecord> searchVerified(String teamId, String query, int limit) {
        if (limit <= 0 || teamId == null || teamId.isBlank()) {
            return Flux.empty();
        }
        var normalized = query == null ? "" : query.strip();
        var hasQuery = !normalized.isEmpty();
        return databaseClient.sql("SELECT " + ORG_COLUMNS + """
                         FROM ai_org_memory
                         WHERE trust_level = 'VERIFIED'
                           AND is_active = TRUE
                           AND (source_type = 'CROSS_TEAM' OR :teamId = ANY(source_team_ids))
                           AND (:hasQuery = FALSE OR content ILIKE :pattern ESCAPE '\\')
                         ORDER BY similarity(content, :query) DESC, cross_vote_count DESC, updated_at DESC
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("hasQuery", hasQuery)
                .bind("pattern", AiMemorySql.likePattern(normalized))
                .bind("query", normalized)
                .bind("limit", limit)
                .map((row, metadata) -> mapOrgMemory(row))
                .all();
    }

    public Mono<OrgMemoryRecord> recordVote(NewOrgMemory memory) {
        return databaseClient.sql("""
                         INSERT INTO ai_org_memory (
                             content, content_type, dedup_hash, trust_level, source_type,
                             source_team_ids, source_user_ids, source_memory_ids, cross_vote_count, is_active
                         ) VALUES (
                             :content, :contentType, :dedupHash, 'PENDING', 'SINGLE',
                             :sourceTeamIds, :sourceUserIds, :sourceMemoryIds, :crossVoteCount, TRUE
                         )
                         ON CONFLICT (dedup_hash) DO UPDATE
                         SET cross_vote_count = ai_org_memory.cross_vote_count + EXCLUDED.cross_vote_count,
                             source_team_ids = ARRAY(SELECT DISTINCT unnest(
                                 ai_org_memory.source_team_ids || EXCLUDED.source_team_ids)),
                             source_user_ids = ARRAY(SELECT DISTINCT unnest(
                                 ai_org_memory.source_user_ids || EXCLUDED.source_user_ids)),
                             source_memory_ids = ARRAY(SELECT DISTINCT unnest(
                                 ai_org_memory.source_memory_ids || EXCLUDED.source_memory_ids)),
                             updated_at = now()
                         RETURNING
                         """ + ORG_COLUMNS)
                .bind("content", memory.content())
                .bind("contentType", memory.contentType())
                .bind("dedupHash", memory.dedupHash())
                .bind("sourceTeamIds", memory.sourceTeamIds().toArray(String[]::new))
                .bind("sourceUserIds", memory.sourceUserIds().toArray(String[]::new))
                .bind("sourceMemoryIds", memory.sourceMemoryIds().toArray(Long[]::new))
                .bind("crossVoteCount", memory.crossVoteCount())
                .map((row, metadata) -> mapOrgMemory(row))
                .one();
    }

    public Mono<OrgMemoryRecord> promoteIfEligible(long id, int threshold) {
        return databaseClient.sql("""
                         UPDATE ai_org_memory
                         SET trust_level = 'VERIFIED',
                             source_type = CASE WHEN cardinality(source_team_ids) > 1
                                 THEN 'CROSS_TEAM' ELSE source_type END,
                             approved_by = 'system',
                             approved_at = now(),
                             is_active = TRUE,
                             updated_at = now()
                         WHERE id = :id
                           AND trust_level = 'PENDING'
                           AND cross_vote_count >= :threshold
                         RETURNING
                         """ + ORG_COLUMNS)
                .bind("id", id)
                .bind("threshold", threshold)
                .map((row, metadata) -> mapOrgMemory(row))
                .one();
    }

    static OrgMemoryRecord mapOrgMemory(Row row) {
        return new OrgMemoryRecord(
                row.get("id", Long.class),
                row.get("content", String.class),
                row.get("content_type", String.class),
                row.get("dedup_hash", String.class),
                row.get("trust_level", String.class),
                row.get("source_type", String.class),
                strings(row.get("source_team_ids", String[].class)),
                strings(row.get("source_user_ids", String[].class)),
                longs(row.get("source_memory_ids", Long[].class)),
                row.get("cross_vote_count", Integer.class),
                row.get("approved_by", String.class),
                row.get("approved_at", Instant.class),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private static List<String> strings(@Nullable String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    private static List<Long> longs(@Nullable Long[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    public record NewOrgMemory(
            String content,
            String contentType,
            String dedupHash,
            List<String> sourceTeamIds,
            List<String> sourceUserIds,
            List<Long> sourceMemoryIds,
            int crossVoteCount) {

        public NewOrgMemory {
            requireText(content, "content");
            requireText(contentType, "contentType");
            requireText(dedupHash, "dedupHash");
            sourceTeamIds = sourceTeamIds == null ? List.of() : List.copyOf(sourceTeamIds);
            sourceUserIds = sourceUserIds == null ? List.of() : List.copyOf(sourceUserIds);
            sourceMemoryIds = sourceMemoryIds == null ? List.of() : List.copyOf(sourceMemoryIds);
            if (crossVoteCount < 1) {
                throw new IllegalArgumentException("crossVoteCount 必须大于 0");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }
}
