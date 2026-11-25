package cn.tolim.pudans.infra.r2dbc.ai;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiDefaultModel;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiDefaultModelRepository {

    private static final String COLUMNS = """
            SELECT id, workspace_id, capability, model_id, created_at, updated_at
              FROM ai_default_models
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiDefaultModel> findAllByWorkspace(String workspaceId) {
        return databaseClient
                .sql(COLUMNS + " WHERE workspace_id = :workspaceId ORDER BY capability")
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiDefaultModel> findByWorkspaceAndCapability(String workspaceId, String capability) {
        return databaseClient
                .sql(COLUMNS + " WHERE workspace_id = :workspaceId AND capability = :capability")
                .bind("workspaceId", workspaceId)
                .bind("capability", capability)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiDefaultModel> insert(AiDefaultModel model) {
        return duplicateAs(
                databaseClient
                        .sql("""
                                INSERT INTO ai_default_models (
                                    id, workspace_id, capability, model_id, created_at, updated_at)
                                VALUES (:id, :workspaceId, :capability, :modelId, :createdAt, :updatedAt)
                                """)
                        .bind("id", model.id())
                        .bind("workspaceId", model.workspaceId())
                        .bind("capability", model.capability())
                        .bind("modelId", model.modelId())
                        .bind("createdAt", utc(model.createdAt()))
                        .bind("updatedAt", utc(model.updatedAt()))
                        .then()
                        .thenReturn(model),
                "ai-default-model-conflict",
                "工作区默认模型已存在。");
    }

    public Mono<AiDefaultModel> update(AiDefaultModel model) {
        return databaseClient
                .sql("""
                        UPDATE ai_default_models
                           SET model_id = :modelId,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .bind("id", model.id())
                .bind("modelId", model.modelId())
                .bind("updatedAt", utc(model.updatedAt()))
                .then()
                .thenReturn(model);
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient
                .sql("DELETE FROM ai_default_models WHERE id = :id")
                .bind("id", id)
                .then();
    }

    public Mono<Boolean> existsByModelId(UUID modelId) {
        return databaseClient
                .sql("SELECT EXISTS (SELECT 1 FROM ai_default_models WHERE model_id = :id)")
                .bind("id", modelId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static AiDefaultModel map(io.r2dbc.spi.Row row) {
        return new AiDefaultModel(
                row.get("id", UUID.class),
                row.get("workspace_id", String.class),
                row.get("capability", String.class),
                row.get("model_id", UUID.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
