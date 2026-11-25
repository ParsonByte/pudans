package cn.tolim.pudans.infra.r2dbc.agent.memory.vector;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryVectorRepository {

    private static final Pattern TABLE_NAME = Pattern.compile("^memory_vectors_[a-z0-9_]{1,40}$");

    private static final String PERSONAL = "personal";

    private static final String POOL = "pool";

    private final DatabaseClient databaseClient;

    public record MemoryVectorHit(UUID memoryId, String namespace, double score) {}

    public Mono<Void> upsert(
            MemoryVectorSpaceEntity space,
            String namespace,
            String ownerId,
            String workspaceId,
            UUID memoryId,
            String layer,
            float[] embedding,
            String contentHash,
            Instant createdAt) {
        var table = tableName(space);
        var castType = storageType(space);
        namespace(namespace);
        var sql = """
                INSERT INTO %s (
                    id, namespace, owner_id, workspace_id, memory_id, layer,
                    embedding_model_id, embedding, content_hash, created_at)
                VALUES (
                    :id, :namespace, :ownerId, :workspaceId, :memoryId, :layer,
                    :embeddingModelId, CAST(:embedding AS %s), :contentHash, :createdAt)
                ON CONFLICT (namespace, memory_id) DO UPDATE
                   SET owner_id = EXCLUDED.owner_id,
                       workspace_id = EXCLUDED.workspace_id,
                       layer = EXCLUDED.layer,
                       embedding_model_id = EXCLUDED.embedding_model_id,
                       embedding = EXCLUDED.embedding,
                       content_hash = EXCLUDED.content_hash,
                       created_at = EXCLUDED.created_at
                """.formatted(table, castType);
        var spec = databaseClient
                .sql(sql)
                .bind("id", UUID.randomUUID())
                .bind("namespace", namespace);
        spec = bindNullable(spec, "ownerId", ownerId, String.class);
        spec = bindNullable(spec, "workspaceId", workspaceId, String.class);
        return spec.bind("memoryId", memoryId)
                .bind("layer", layer)
                .bind("embeddingModelId", space.getEmbeddingModelId())
                .bind("embedding", vectorLiteral(embedding))
                .bind("contentHash", contentHash)
                .bind("createdAt", utc(createdAt))
                .then();
    }

    public Flux<MemoryVectorHit> searchPool(
            MemoryVectorSpaceEntity space, float[] embedding, int limit) {
        return search(space, POOL, null, null, embedding, limit);
    }

    public Flux<MemoryVectorHit> searchPersonal(
            MemoryVectorSpaceEntity space,
            String ownerId,
            String workspaceId,
            float[] embedding,
            int limit) {
        return search(space, PERSONAL, ownerId, workspaceId, embedding, limit);
    }

    private Flux<MemoryVectorHit> search(
            MemoryVectorSpaceEntity space,
            String namespace,
            String ownerId,
            String workspaceId,
            float[] embedding,
            int limit) {
        namespace(namespace);
        var table = tableName(space);
        var castType = storageType(space);
        var sql = """
                SELECT memory_id, namespace,
                       1 - (embedding <=> CAST(:embedding AS %s)) AS score
                  FROM %s
                 WHERE namespace = :namespace
                """.formatted(castType, table);
        if (PERSONAL.equals(namespace)) {
            sql += "\n   AND owner_id = :ownerId\n   AND workspace_id = :workspaceId";
        }
        sql += "\n ORDER BY embedding <=> CAST(:embedding AS " + castType + ")\n LIMIT :limit";
        var spec = databaseClient
                .sql(sql)
                .bind("namespace", namespace)
                .bind("embedding", vectorLiteral(embedding));
        if (PERSONAL.equals(namespace)) {
            spec = spec.bind("ownerId", ownerId).bind("workspaceId", workspaceId);
        }
        return spec.bind("limit", limit)
                .map((row, metadata) -> new MemoryVectorHit(
                        row.get("memory_id", UUID.class),
                        row.get("namespace", String.class),
                        doubleValue(row.get("score", Double.class))))
                .all();
    }

    public static String vectorLiteral(float[] values) {
        var builder = new StringBuilder(values.length * 12).append('[');
        for (var index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values[index]));
        }
        return builder.append(']').toString();
    }

    private static String tableName(MemoryVectorSpaceEntity space) {
        if (space == null || space.getTableName() == null
                || !TABLE_NAME.matcher(space.getTableName()).matches()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "memory-vector-space-invalid",
                    "向量空间表名不合法。");
        }
        return space.getTableName();
    }

    private static String storageType(MemoryVectorSpaceEntity space) {
        var type = space.getStorageType();
        if ("vector".equals(type) || "halfvec".equals(type)) {
            return type;
        }
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "memory-vector-space-invalid",
                "向量空间存储类型不合法。");
    }

    private static void namespace(String namespace) {
        if (!PERSONAL.equals(namespace) && !POOL.equals(namespace)) {
            throw new IllegalArgumentException("namespace must be personal or pool");
        }
    }

    private static double doubleValue(Double value) {
        return value == null ? 0 : value;
    }
}
