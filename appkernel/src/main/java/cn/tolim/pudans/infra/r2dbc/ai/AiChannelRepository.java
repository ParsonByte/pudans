package cn.tolim.pudans.infra.r2dbc.ai;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiChannelRepository {

    private static final String COLUMNS = """
            SELECT id, workspace_id, provider, protocol, base_url, secret_ref,
                   secret_keys_jsonb::text AS secret_keys,
                   credential_ciphertext_jsonb::text AS credential_ciphertext,
                   credential_status_jsonb::text AS credential_status,
                   status, timeout_ms, max_concurrency, weight, rate_limit,
                   config_jsonb::text AS config, created_at, updated_at
              FROM ai_channels
            """;

    private static final String INSERT = """
            INSERT INTO ai_channels (
                id, workspace_id, provider, protocol, base_url, secret_ref,
                secret_keys_jsonb, credential_ciphertext_jsonb, credential_status_jsonb,
                status, timeout_ms, max_concurrency, weight, rate_limit, config_jsonb,
                created_at, updated_at)
            VALUES (
                :id, :workspaceId, :provider, :protocol, :baseUrl, :secretRef,
                CAST(:secretKeys AS jsonb), CAST(:credentialCiphertext AS jsonb),
                CAST(:credentialStatus AS jsonb), :status, :timeoutMs, :maxConcurrency,
                :weight, :rateLimit, CAST(:config AS jsonb), :createdAt, :updatedAt)
            """;

    private static final String UPDATE = """
            UPDATE ai_channels
               SET provider = :provider,
                   protocol = :protocol,
                   base_url = :baseUrl,
                   secret_ref = :secretRef,
                   secret_keys_jsonb = CAST(:secretKeys AS jsonb),
                   credential_ciphertext_jsonb = CAST(:credentialCiphertext AS jsonb),
                   credential_status_jsonb = CAST(:credentialStatus AS jsonb),
                   status = :status,
                   timeout_ms = :timeoutMs,
                   max_concurrency = :maxConcurrency,
                   weight = :weight,
                   rate_limit = :rateLimit,
                   config_jsonb = CAST(:config AS jsonb),
                   updated_at = :updatedAt
             WHERE id = :id
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiChannelEntity> findAllByWorkspace(String workspaceId) {
        return databaseClient
                .sql(COLUMNS + " WHERE workspace_id = :workspaceId ORDER BY created_at, id")
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<AiChannelEntity> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY workspace_id, created_at, id")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiChannelEntity> findByWorkspaceAndId(String workspaceId, UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE workspace_id = :workspaceId AND id = :id")
                .bind("workspaceId", workspaceId)
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiChannelEntity> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiChannelEntity> insert(AiChannelEntity entity) {
        var spec = databaseClient
                .sql(INSERT)
                .bind("id", entity.getId())
                .bind("workspaceId", entity.getWorkspaceId())
                .bind("provider", entity.getProvider())
                .bind("protocol", entity.getProtocol());
        spec = bindNullable(spec, "baseUrl", entity.getBaseUrl(), String.class);
        spec = bindNullable(spec, "secretRef", entity.getSecretRef(), String.class);
        spec = spec.bind("secretKeys", AiJsonCodec.write(entity.getSecretKeys()))
                .bind("credentialCiphertext", AiJsonCodec.write(entity.getCredentialCiphertext()))
                .bind("credentialStatus", AiJsonCodec.write(entity.getCredentialStatus()))
                .bind("status", entity.getStatus())
                .bind("timeoutMs", entity.getTimeoutMs())
                .bind("maxConcurrency", entity.getMaxConcurrency())
                .bind("weight", entity.getWeight());
        spec = bindNullable(spec, "rateLimit", entity.getRateLimit(), Integer.class);
        return duplicateAs(
                spec.bind("config", AiJsonCodec.write(entity.getConfig()))
                        .bind("createdAt", utc(entity.getCreatedAt()))
                        .bind("updatedAt", utc(entity.getUpdatedAt()))
                        .then()
                        .thenReturn(entity),
                "ai-channel-conflict",
                "AI 渠道已存在或配置冲突。");
    }

    public Mono<AiChannelEntity> update(AiChannelEntity entity) {
        var spec = databaseClient
                .sql(UPDATE)
                .bind("id", entity.getId())
                .bind("provider", entity.getProvider())
                .bind("protocol", entity.getProtocol());
        spec = bindNullable(spec, "baseUrl", entity.getBaseUrl(), String.class);
        spec = bindNullable(spec, "secretRef", entity.getSecretRef(), String.class);
        spec = spec.bind("secretKeys", AiJsonCodec.write(entity.getSecretKeys()))
                .bind("credentialCiphertext", AiJsonCodec.write(entity.getCredentialCiphertext()))
                .bind("credentialStatus", AiJsonCodec.write(entity.getCredentialStatus()))
                .bind("status", entity.getStatus())
                .bind("timeoutMs", entity.getTimeoutMs())
                .bind("maxConcurrency", entity.getMaxConcurrency())
                .bind("weight", entity.getWeight());
        spec = bindNullable(spec, "rateLimit", entity.getRateLimit(), Integer.class);
        return duplicateAs(
                spec.bind("config", AiJsonCodec.write(entity.getConfig()))
                        .bind("updatedAt", utc(entity.getUpdatedAt()))
                        .then()
                        .thenReturn(entity),
                "ai-channel-conflict",
                "AI 渠道配置冲突。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient.sql("DELETE FROM ai_channels WHERE id = :id").bind("id", id).then();
    }

    private static AiChannelEntity map(io.r2dbc.spi.Row row) {
        var entity = new AiChannelEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setWorkspaceId(row.get("workspace_id", String.class));
        entity.setProvider(row.get("provider", String.class));
        entity.setProtocol(row.get("protocol", String.class));
        entity.setBaseUrl(row.get("base_url", String.class));
        entity.setSecretRef(row.get("secret_ref", String.class));
        entity.setSecretKeys(AiJsonCodec.readStrings(row.get("secret_keys", String.class)));
        entity.setCredentialCiphertext(
                AiJsonCodec.readStrings(row.get("credential_ciphertext", String.class)));
        entity.setCredentialStatus(
                AiJsonCodec.readObject(row.get("credential_status", String.class)));
        entity.setStatus(row.get("status", String.class));
        entity.setTimeoutMs(row.get("timeout_ms", Integer.class));
        entity.setMaxConcurrency(row.get("max_concurrency", Integer.class));
        entity.setWeight(row.get("weight", Integer.class));
        entity.setRateLimit(row.get("rate_limit", Integer.class));
        entity.setConfig(AiJsonCodec.readObject(row.get("config", String.class)));
        entity.setCreatedAt(instant(row.get("created_at", java.time.OffsetDateTime.class)));
        entity.setUpdatedAt(instant(row.get("updated_at", java.time.OffsetDateTime.class)));
        return entity;
    }
}
