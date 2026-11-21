package cn.tolim.pudans.core.infra.r2dbc.ai.rag;

import cn.tolim.pudans.core.ai.rag.KnowledgeBaseQueryLog;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
public class AiKbQueryLogRepository {

    private final DatabaseClient databaseClient;

    public Mono<Long> insert(KnowledgeBaseQueryLog queryLog) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_kb_query_log (
                            user_id, request_id, kb_names, query, hit_chunk_ids, hit_count,
                            top_score, rerank_used, latency_ms
                        ) VALUES (
                            :userId, :requestId, :kbNames, :query, :hitChunkIds, :hitCount,
                            :topScore, :rerankUsed, :latencyMs
                        )
                        RETURNING id
                        """)
                .bind("userId", queryLog.getUserId())
                .bind("kbNames", nullSafe(queryLog.getKbNames()).toArray(String[]::new))
                .bind("query", queryLog.getQuery())
                .bind("hitChunkIds", nullSafe(queryLog.getHitChunkIds()).toArray(Long[]::new))
                .bind("hitCount", queryLog.getHitCount())
                .bind("rerankUsed", queryLog.isRerankUsed());
        spec = AiR2dbcBindings.bindNullable(spec, "requestId", queryLog.getRequestId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "topScore", queryLog.getTopScore(), BigDecimal.class);
        spec = AiR2dbcBindings.bindNullable(spec, "latencyMs", queryLog.getLatencyMs(), Integer.class);
        return spec.map((row, metadata) -> row.get("id", Long.class)).one();
    }

    public Flux<KnowledgeBaseQueryLog> findRecentByKbName(String kbName, int limit) {
        return databaseClient.sql("""
                        SELECT id, user_id, request_id, kb_names, query, hit_chunk_ids, hit_count,
                               top_score, rerank_used, latency_ms, created_at
                        FROM ai_kb_query_log
                        WHERE :kbName = ANY(kb_names)
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .bind("kbName", kbName)
                .bind("limit", limit)
                .map((row, metadata) -> queryLog(row))
                .all();
    }

    static KnowledgeBaseQueryLog queryLog(Row row) {
        var queryLog = new KnowledgeBaseQueryLog();
        queryLog.setId(row.get("id", Long.class));
        queryLog.setUserId(row.get("user_id", String.class));
        queryLog.setRequestId(row.get("request_id", String.class));
        queryLog.setKbNames(arrayValues(row.get("kb_names", String[].class)));
        queryLog.setQuery(row.get("query", String.class));
        queryLog.setHitChunkIds(arrayValues(row.get("hit_chunk_ids", Long[].class)));
        var hitCount = row.get("hit_count", Integer.class);
        queryLog.setHitCount(hitCount == null ? 0 : hitCount);
        queryLog.setTopScore(row.get("top_score", BigDecimal.class));
        queryLog.setRerankUsed(Boolean.TRUE.equals(row.get("rerank_used", Boolean.class)));
        queryLog.setLatencyMs(row.get("latency_ms", Integer.class));
        var createdAt = row.get("created_at", OffsetDateTime.class);
        queryLog.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return queryLog;
    }

    private static <T> List<T> arrayValues(@Nullable T[] values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(values));
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
