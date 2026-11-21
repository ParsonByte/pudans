package cn.tolim.pudans.infra.r2dbc.ai.rag;

import cn.tolim.pudans.core.ai.rag.KnowledgeBaseChunk;
import cn.tolim.pudans.core.ai.rag.RetrievalCandidate;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import cn.tolim.pudans.infra.r2dbc.ai.vector.VectorCodec;
import io.r2dbc.spi.Row;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiKbChunkRepository {

    private final DatabaseClient databaseClient;

    public Flux<KnowledgeBaseChunk> insertAll(List<KnowledgeBaseChunk> chunks) {
        return Flux.fromIterable(chunks).concatMap(this::insert);
    }

    public Mono<Void> deleteByDocumentId(long documentId) {
        return databaseClient.sql("DELETE FROM ai_kb_chunk WHERE document_id = :documentId")
                .bind("documentId", documentId)
                .then();
    }

    public Mono<Boolean> updateEmbedding(long chunkId, float[] embedding) {
        var sql = "UPDATE ai_kb_chunk SET embedding = CAST(:embedding AS vector) WHERE id = :chunkId";
        return databaseClient.sql(sql)
                .bind("embedding", VectorCodec.format(embedding))
                .bind("chunkId", chunkId)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Flux<RetrievalCandidate> searchKeyword(List<String> kbNames, String query, int limit) {
        return databaseClient.sql("""
                        WITH terms AS (SELECT :queryTerm AS term)
                        SELECT c.id, c.document_id, c.kb_name, d.file_name, c.content, c.page_no, c.heading_path,
                               CAST(similarity(c.content, terms.term) AS double precision) AS score
                        FROM ai_kb_chunk c
                        JOIN ai_kb_document d ON d.id = c.document_id
                        CROSS JOIN terms
                        WHERE c.kb_name = ANY(:kbNames)
                          AND d.status = 'READY'
                          AND c.content % terms.term
                        ORDER BY score DESC
                        LIMIT :limit
                        """)
                .bind("kbNames", kbNames.toArray(String[]::new))
                .bind("queryTerm", query)
                .bind("limit", limit)
                .map((row, metadata) -> candidate(row))
                .all();
    }

    public Flux<VectorCandidate> searchVectorCandidates(List<String> kbNames, float[] queryVector, int limit) {
        return databaseClient.sql("""
                        WITH query_vector AS (SELECT CAST(:queryVector AS vector) AS embedding)
                        SELECT c.id, c.document_id, c.kb_name, d.file_name, c.content, c.page_no, c.heading_path,
                               1 - (c.embedding <=> query_vector.embedding) AS score,
                               CAST(c.embedding AS text) AS embedding
                        FROM ai_kb_chunk c
                        JOIN ai_kb_document d ON d.id = c.document_id
                        CROSS JOIN query_vector
                        WHERE c.kb_name = ANY(:kbNames)
                          AND d.status = 'READY'
                          AND c.embedding IS NOT NULL
                        ORDER BY c.embedding <=> query_vector.embedding
                        LIMIT :limit
                        """)
                .bind("kbNames", kbNames.toArray(String[]::new))
                .bind("queryVector", VectorCodec.format(queryVector))
                .bind("limit", limit)
                .map((row, metadata) -> new VectorCandidate(
                        candidate(row), VectorCodec.parse(row.get("embedding", String.class))))
                .all();
    }

    static RetrievalCandidate candidate(Row row) {
        var score = row.get("score", Double.class);
        return new RetrievalCandidate(
                requiredLong(row, "id"),
                requiredLong(row, "document_id"),
                row.get("kb_name", String.class),
                row.get("file_name", String.class),
                row.get("content", String.class),
                row.get("page_no", Integer.class),
                row.get("heading_path", String.class),
                score == null ? 0.0 : score);
    }

    private Mono<KnowledgeBaseChunk> insert(KnowledgeBaseChunk chunk) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_kb_chunk (
                            kb_name, document_id, chunk_index, content, token_count, page_no, heading_path
                        ) VALUES (
                            :kbName, :documentId, :chunkIndex, :content, :tokenCount, :pageNo, :headingPath
                        )
                        RETURNING id
                        """)
                .bind("kbName", chunk.getKbName())
                .bind("documentId", chunk.getDocumentId())
                .bind("chunkIndex", chunk.getChunkIndex())
                .bind("content", chunk.getContent())
                .bind("tokenCount", chunk.getTokenCount());
        spec = AiR2dbcBindings.bindNullable(spec, "pageNo", chunk.getPageNo(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "headingPath", chunk.getHeadingPath(), String.class);
        return spec.map((row, metadata) -> {
                    chunk.setId(row.get("id", Long.class));
                    return chunk;
                })
                .one();
    }

    public record VectorCandidate(RetrievalCandidate candidate, float[] embedding) {}

    private static long requiredLong(Row row, String column) {
        var value = row.get(column, Long.class);
        if (value == null) {
            throw new IllegalStateException("数据库列 " + column + " 不能为空。");
        }
        return value;
    }
}
