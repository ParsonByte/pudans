package cn.tolim.pudans.infra.r2dbc.ai.stats;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiStatsRepository {

    private final DatabaseClient databaseClient;

    public Flux<RoutingStatsRow> routing(int days) {
        return databaseClient.sql("""
                        SELECT COALESCE(selected_provider, 'unknown') AS provider,
                               COALESCE(selected_model, 'unknown') AS model,
                               status,
                               COUNT(*) AS requests,
                               COALESCE(SUM(points), 0) AS points
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY selected_provider, selected_model, status
                        ORDER BY requests DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new RoutingStatsRow(
                        row.get("provider", String.class),
                        row.get("model", String.class),
                        row.get("status", String.class),
                        count(row, "requests"),
                        decimal(row, "points")))
                .all();
    }

    public Flux<PointStatsRow> points(int days) {
        return databaseClient.sql("""
                        SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS day,
                               COALESCE(selected_model, 'unknown') AS model,
                               COUNT(*) AS requests,
                               COALESCE(SUM(points), 0) AS points,
                               COALESCE(SUM(total_tokens), 0) AS tokens
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY day, selected_model
                        ORDER BY day DESC, points DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new PointStatsRow(
                        row.get("day", String.class),
                        row.get("model", String.class),
                        count(row, "requests"),
                        decimal(row, "points"),
                        row.get("tokens", Long.class)))
                .all();
    }

    public Mono<MemoryStatsRow> memory() {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_memory_l1) AS l1_total,
                            (SELECT COUNT(*) FROM ai_memory_l1 WHERE status = 'ACTIVE') AS l1_active,
                            (SELECT COUNT(*) FROM ai_memory_l2) AS l2_total,
                            (SELECT COUNT(*) FROM ai_memory_l3) AS l3_total
                        """)
                .map((row, metadata) -> new MemoryStatsRow(
                        count(row, "l1_total"),
                        count(row, "l1_active"),
                        count(row, "l2_total"),
                        count(row, "l3_total")))
                .one();
    }

    public Mono<KnowledgeBaseStatsRow> knowledgeBase() {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_kb_document) AS documents,
                            (SELECT COUNT(*) FROM ai_kb_document WHERE status = 'READY') AS ready,
                            (SELECT COUNT(*) FROM ai_kb_document WHERE status = 'FAILED') AS failed,
                            (SELECT COUNT(*) FROM ai_kb_chunk) AS chunks,
                            (SELECT COUNT(*) FROM ai_kb_query_log) AS queries
                        """)
                .map((row, metadata) -> new KnowledgeBaseStatsRow(
                        count(row, "documents"),
                        count(row, "ready"),
                        count(row, "failed"),
                        count(row, "chunks"),
                        count(row, "queries")))
                .one();
    }

    private static long count(Row row, String column) {
        var value = row.get(column, Long.class);
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(Row row, String column) {
        var value = row.get(column, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    public record RoutingStatsRow(
            String provider, String model, String status, long requests, BigDecimal points) {}

    public record PointStatsRow(
            String day, String model, long requests, BigDecimal points, long tokens) {}

    public record MemoryStatsRow(long l1Total, long l1Active, long l2Total, long l3Total) {}

    public record KnowledgeBaseStatsRow(
            long documents, long ready, long failed, long chunks, long queries) {}
}
