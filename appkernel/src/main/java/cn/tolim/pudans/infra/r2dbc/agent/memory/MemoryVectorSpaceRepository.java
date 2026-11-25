package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

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
public class MemoryVectorSpaceRepository {

    private static final String COLUMNS = """
            SELECT id, embedding_model_id, model_key, table_name, storage_type, dimension,
                   metric, hnsw_index_name, status, created_at, updated_at
              FROM memory_vector_spaces
            """;

    private final DatabaseClient databaseClient;

    public record TableShape(String columnType) {}

    public record IndexShape(String indexMethod, String columnName, String opclass) {}

    public Flux<MemoryVectorSpaceEntity> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY model_key, id")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<MemoryVectorSpaceEntity> findByModelKey(String modelKey) {
        return databaseClient
                .sql(COLUMNS + " WHERE model_key = :modelKey")
                .bind("modelKey", modelKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemoryVectorSpaceEntity> findActiveByModelId(UUID embeddingModelId) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE embedding_model_id = :embeddingModelId
                           AND status = 'active'
                         ORDER BY updated_at DESC
                        """)
                .bind("embeddingModelId", embeddingModelId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<Boolean> tableExists(String tableName) {
        return databaseClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM information_schema.tables
                             WHERE table_schema = current_schema()
                               AND table_name = :tableName)
                        """)
                .bind("tableName", tableName)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<TableShape> tableShape(String tableName) {
        return databaseClient
                .sql("""
                        SELECT format_type(attribute.atttypid, attribute.atttypmod) AS column_type
                          FROM pg_class table_class
                          JOIN pg_namespace table_schema
                            ON table_schema.oid = table_class.relnamespace
                           AND table_schema.nspname = current_schema()
                          JOIN pg_attribute attribute
                            ON attribute.attrelid = table_class.oid
                           AND attribute.attname = 'embedding'
                           AND attribute.attnum > 0
                           AND NOT attribute.attisdropped
                         WHERE table_class.relname = :tableName
                           AND table_class.relkind IN ('r', 'p')
                        """)
                .bind("tableName", tableName)
                .map((row, metadata) -> new TableShape(row.get("column_type", String.class)))
                .one();
    }

    public Mono<IndexShape> indexShape(String tableName, String indexName) {
        return databaseClient
                .sql("""
                        SELECT access_method.amname AS index_method,
                               attribute.attname AS column_name,
                               operator_class.opcname AS index_opclass
                          FROM pg_class index_class
                          JOIN pg_namespace index_schema
                            ON index_schema.oid = index_class.relnamespace
                           AND index_schema.nspname = current_schema()
                          JOIN pg_index index_definition
                            ON index_definition.indexrelid = index_class.oid
                          JOIN pg_class table_class
                            ON table_class.oid = index_definition.indrelid
                           AND table_class.relnamespace = index_schema.oid
                          JOIN pg_am access_method ON access_method.oid = index_class.relam
                          JOIN LATERAL unnest(index_definition.indkey)
                               WITH ORDINALITY AS index_key(attnum, ordinal)
                            ON TRUE
                          JOIN LATERAL unnest(index_definition.indclass)
                               WITH ORDINALITY AS class_key(opclass_oid, ordinal)
                            ON class_key.ordinal = index_key.ordinal
                          JOIN pg_opclass operator_class ON operator_class.oid = class_key.opclass_oid
                          JOIN pg_attribute attribute
                            ON attribute.attrelid = table_class.oid
                           AND attribute.attnum = index_key.attnum
                         WHERE table_class.relname = :tableName
                           AND index_class.relname = :indexName
                         ORDER BY index_key.ordinal
                         LIMIT 1
                        """)
                .bind("tableName", tableName)
                .bind("indexName", indexName)
                .map((row, metadata) -> new IndexShape(
                        row.get("index_method", String.class),
                        row.get("column_name", String.class),
                        row.get("index_opclass", String.class)))
                .one();
    }

    public Mono<MemoryVectorSpaceEntity> insert(MemoryVectorSpaceEntity entity) {
        return duplicateAs(
                databaseClient
                        .sql("""
                                INSERT INTO memory_vector_spaces (
                                    id, embedding_model_id, model_key, table_name, storage_type,
                                    dimension, metric, hnsw_index_name, status, created_at, updated_at)
                                VALUES (
                                    :id, :embeddingModelId, :modelKey, :tableName, :storageType,
                                    :dimension, :metric, :hnswIndexName, :status, :createdAt, :updatedAt)
                                """)
                        .bind("id", entity.getId())
                        .bind("embeddingModelId", entity.getEmbeddingModelId())
                        .bind("modelKey", entity.getModelKey())
                        .bind("tableName", entity.getTableName())
                        .bind("storageType", entity.getStorageType())
                        .bind("dimension", entity.getDimension())
                        .bind("metric", entity.getMetric())
                        .bind("hnswIndexName", entity.getHnswIndexName())
                        .bind("status", entity.getStatus())
                        .bind("createdAt", utc(entity.getCreatedAt()))
                        .bind("updatedAt", utc(entity.getUpdatedAt()))
                        .then()
                        .thenReturn(entity),
                "memory-vector-space-conflict",
                "Embedding 模型、模型键或向量表已登记。");
    }

    public Mono<Void> updateStatus(UUID id, String status, Instant updatedAt) {
        return databaseClient
                .sql("""
                        UPDATE memory_vector_spaces
                           SET status = :status, updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .bind("id", id)
                .bind("status", status)
                .bind("updatedAt", utc(updatedAt))
                .then();
    }

    static MemoryVectorSpaceEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemoryVectorSpaceEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setEmbeddingModelId(row.get("embedding_model_id", UUID.class));
        entity.setModelKey(row.get("model_key", String.class));
        entity.setTableName(row.get("table_name", String.class));
        entity.setStorageType(row.get("storage_type", String.class));
        entity.setDimension(value(row.get("dimension", Integer.class)));
        entity.setMetric(row.get("metric", String.class));
        entity.setHnswIndexName(row.get("hnsw_index_name", String.class));
        entity.setStatus(row.get("status", String.class));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        entity.setUpdatedAt(instant(row.get("updated_at", OffsetDateTime.class)));
        return entity;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
