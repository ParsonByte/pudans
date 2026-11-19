package cn.tolim.pudans.infra.r2dbc.ai.rag;

import cn.tolim.pudans.core.ai.rag.DocumentStatus;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseDocument;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiKbDocumentRepository {

    private static final int DEFAULT_VERSION = 1;

    private final DatabaseClient databaseClient;

    public Mono<Long> insertPending(KnowledgeBaseDocument document) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_kb_document (
                            kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                            status, chunk_count, token_count, error_msg, version, uploaded_by
                        ) VALUES (
                            :kbName, :storageFileId, :fileName, :mimeType, :fileSize, :checksum,
                            :status, 0, 0, NULL, :version, :uploadedBy
                        )
                        ON CONFLICT (kb_name, checksum) DO NOTHING
                        RETURNING id
                        """)
                .bind("kbName", document.getKbName())
                .bind("storageFileId", document.getStorageFileId())
                .bind("fileName", document.getFileName())
                .bind("checksum", document.getChecksum())
                .bind("status", DocumentStatus.PENDING.name())
                .bind("version", document.getVersion());
        spec = AiR2dbcBindings.bindNullable(spec, "mimeType", document.getMimeType(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "fileSize", document.getFileSize(), Long.class);
        spec = AiR2dbcBindings.bindNullable(spec, "uploadedBy", document.getUploadedBy(), String.class);
        return spec.map((row, metadata) -> row.get("id", Long.class)).one();
    }

    public Mono<KnowledgeBaseDocument> findByKbNameAndChecksum(String kbName, String checksum) {
        return databaseClient.sql("""
                        SELECT id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                               status, chunk_count, token_count, error_msg, version, uploaded_by,
                               created_at, updated_at
                        FROM ai_kb_document
                        WHERE kb_name = :kbName AND checksum = :checksum
                        """)
                .bind("kbName", kbName)
                .bind("checksum", checksum)
                .map((row, metadata) -> document(row))
                .one();
    }

    public Mono<KnowledgeBaseDocument> findByIdAndKbName(long id, String kbName) {
        return databaseClient.sql("""
                        SELECT id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                               status, chunk_count, token_count, error_msg, version, uploaded_by,
                               created_at, updated_at
                        FROM ai_kb_document
                        WHERE id = :id AND kb_name = :kbName
                        """)
                .bind("id", id)
                .bind("kbName", kbName)
                .map((row, metadata) -> document(row))
                .one();
    }

    public Flux<KnowledgeBaseDocument> findByKbNameAndStatus(String kbName, DocumentStatus status) {
        return databaseClient.sql("""
                        SELECT id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                               status, chunk_count, token_count, error_msg, version, uploaded_by,
                               created_at, updated_at
                        FROM ai_kb_document
                        WHERE kb_name = :kbName AND status = :status
                        ORDER BY id
                        """)
                .bind("kbName", kbName)
                .bind("status", status.name())
                .map((row, metadata) -> document(row))
                .all();
    }

    public Flux<KnowledgeBaseDocument> listByKbName(
            String kbName, @Nullable DocumentStatus status, int limit, long offset) {
        var sql = status == null
                ? """
                        SELECT id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                               status, chunk_count, token_count, error_msg, version, uploaded_by,
                               created_at, updated_at
                        FROM ai_kb_document
                        WHERE kb_name = :kbName
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """
                : """
                        SELECT id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                               status, chunk_count, token_count, error_msg, version, uploaded_by,
                               created_at, updated_at
                        FROM ai_kb_document
                        WHERE kb_name = :kbName AND status = :status
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """;
        var spec = databaseClient.sql(sql)
                .bind("kbName", kbName)
                .bind("limit", limit)
                .bind("offset", offset);
        if (status != null) {
            spec = spec.bind("status", status.name());
        }
        return spec.map((row, metadata) -> document(row)).all();
    }

    public Mono<Long> countByKbName(String kbName, @Nullable DocumentStatus status) {
        var sql = status == null
                ? "SELECT COUNT(*) AS total FROM ai_kb_document WHERE kb_name = :kbName"
                : "SELECT COUNT(*) AS total FROM ai_kb_document WHERE kb_name = :kbName AND status = :status";
        var spec = databaseClient.sql(sql).bind("kbName", kbName);
        if (status != null) {
            spec = spec.bind("status", status.name());
        }
        return spec.map((row, metadata) -> row.get("total", Long.class)).one();
    }

    public Mono<Long> deleteByIdAndKbName(long id, String kbName) {
        return databaseClient.sql("DELETE FROM ai_kb_document WHERE id = :id AND kb_name = :kbName")
                .bind("id", id)
                .bind("kbName", kbName)
                .fetch()
                .rowsUpdated();
    }

    public Mono<KnowledgeBaseDocument> updateStatus(
            long id, DocumentStatus status, int chunkCount, int tokenCount, @Nullable String errorMsg) {
        var spec = databaseClient.sql("""
                        UPDATE ai_kb_document
                        SET status = :status, chunk_count = :chunkCount, token_count = :tokenCount,
                            error_msg = :errorMsg, updated_at = now()
                        WHERE id = :id
                        RETURNING id, kb_name, storage_file_id, file_name, mime_type, file_size, checksum,
                                  status, chunk_count, token_count, error_msg, version, uploaded_by,
                                  created_at, updated_at
                        """)
                .bind("id", id)
                .bind("status", status.name())
                .bind("chunkCount", chunkCount)
                .bind("tokenCount", tokenCount);
        spec = AiR2dbcBindings.bindNullable(spec, "errorMsg", errorMsg, String.class);
        return spec.map((row, metadata) -> document(row)).one();
    }

    static KnowledgeBaseDocument document(Row row) {
        var document = new KnowledgeBaseDocument();
        document.setId(row.get("id", Long.class));
        document.setKbName(row.get("kb_name", String.class));
        document.setStorageFileId(row.get("storage_file_id", String.class));
        document.setFileName(row.get("file_name", String.class));
        document.setMimeType(row.get("mime_type", String.class));
        document.setFileSize(row.get("file_size", Long.class));
        document.setChecksum(row.get("checksum", String.class));
        var status = row.get("status", String.class);
        document.setStatus(status == null ? DocumentStatus.PENDING : DocumentStatus.valueOf(status));
        document.setChunkCount(intValue(row.get("chunk_count", Integer.class)));
        document.setTokenCount(intValue(row.get("token_count", Integer.class)));
        document.setErrorMsg(row.get("error_msg", String.class));
        document.setVersion(positiveVersion(row.get("version", Integer.class)));
        document.setUploadedBy(row.get("uploaded_by", String.class));
        document.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        document.setUpdatedAt(instant(row.get("updated_at", OffsetDateTime.class)));
        return document;
    }

    private static int intValue(@Nullable Integer value) {
        return value == null ? 0 : value;
    }

    private static int positiveVersion(@Nullable Integer value) {
        return value == null || value < 1 ? DEFAULT_VERSION : value;
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
