package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryVectorSpace;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceStatus;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceSyncResult;
import cn.tolim.pudans.contract.agent.dto.MemoryVectorSpaceValidationResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapability;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapabilityProbe;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryVectorSpaces {

    private static final String ACTIVE = "active";

    private static final String DISABLED = "disabled";

    private static final String MISSING = "missing";

    private static final String COSINE = "cosine";

    private static final String HNSW = "hnsw";

    private static final String VECTOR = "vector";

    private static final String HALFVEC = "halfvec";

    private static final String EMBEDDING_COLUMN = "embedding";

    private static final String READY = "memory-vector-ready";

    private static final String MODEL_UNAVAILABLE = "memory-vector-model-unavailable";

    private static final String PGVECTOR_UNAVAILABLE = "memory-vector-pgvector-unavailable";

    private static final String TABLE_MISSING = "memory-vector-table-missing";

    private static final String COLUMN_MISMATCH = "memory-vector-column-mismatch";

    private static final String INDEX_MISMATCH = "memory-vector-index-mismatch";

    private static final String MODEL_KEY_INVALID = "memory-vector-model-key-invalid";

    private static final String TABLE_NAME_INVALID = "memory-vector-table-name-invalid";

    private static final String STORAGE_TYPE_INVALID = "memory-vector-storage-type-invalid";

    private static final String DIMENSION_INVALID = "memory-vector-dimension-invalid";

    private static final String METRIC_INVALID = "memory-vector-metric-invalid";

    private static final String INDEX_NAME_INVALID = "memory-vector-index-name-invalid";

    private final MemoryVectorSpaceRepository spaces;

    private final AiModelRepository models;

    private final PgVectorCapabilityProbe capabilityProbe;

    public Flux<MemoryVectorSpace> list() {
        return spaces.findAll().map(MemoryVectorSpaceEntity::toContract);
    }

    public Mono<MemoryVectorSpace> find(String modelKey) {
        return require(modelKey).map(MemoryVectorSpaceEntity::toContract);
    }

    // 创建前必须验证模型、物理列和 HNSW 索引一致，避免登记不可用空间。
    public Mono<MemoryVectorSpace> create(
            String workspaceId, MemoryVectorSpaceCreateRequest request) {
        return validate(workspaceId, request)
                .flatMap(result -> result.valid()
                        ? spaces.insert(entity(request)).map(MemoryVectorSpaceEntity::toContract)
                        : Mono.error(invalid(result)));
    }

    public Mono<MemoryVectorSpaceValidationResult> validate(
            String workspaceId, MemoryVectorSpaceCreateRequest request) {
        var normalized = normalize(request);
        return capabilityProbe.probe().flatMap(capability -> {
            if (!capability.usable()) {
                return Mono.just(invalid(request, PGVECTOR_UNAVAILABLE, null, null));
            }
            return models
                    .findById(normalized.embeddingModelId())
                    .filter(model -> ACTIVE.equals(model.status())
                            && model.capabilities() != null
                            && model.capabilities().containsKey("embedding"))
                    .flatMap(model -> models
                            .existsAvailable(normalized.embeddingModelId(), workspaceId)
                            .flatMap(available -> Boolean.TRUE.equals(available)
                                    ? physical(normalized)
                                    : Mono.just(invalid(
                                            normalized, MODEL_UNAVAILABLE, null, null))))
                    .defaultIfEmpty(invalid(normalized, MODEL_UNAVAILABLE, null, null));
        });
    }

    // 启用前重新验证真实物理资源，不能只改控制表状态。
    public Mono<MemoryVectorSpace> enable(String workspaceId, String modelKey) {
        return require(modelKey).flatMap(space -> validate(workspaceId, request(space))
                .flatMap(result -> {
                    if (!result.valid()) {
                        return Mono.error(invalid(result));
                    }
                    var updatedAt = Instant.now();
                    return spaces.updateStatus(space.getId(), ACTIVE, updatedAt)
                            .thenReturn(space.toContract(ACTIVE, updatedAt));
                }));
    }

    public Mono<MemoryVectorSpace> disable(String modelKey) {
        return require(modelKey).flatMap(space -> {
            var updatedAt = Instant.now();
            return spaces.updateStatus(space.getId(), DISABLED, updatedAt)
                    .thenReturn(space.toContract(DISABLED, updatedAt));
        });
    }

    public Mono<MemoryVectorSpaceStatus> status(String workspaceId, String modelKey) {
        return require(modelKey).flatMap(space -> {
            if (DISABLED.equals(space.getStatus())) {
                return Mono.just(new MemoryVectorSpaceStatus(
                        space.getModelKey(),
                        space.getTableName(),
                        DISABLED,
                        false,
                        "memory-vector-space-disabled",
                        Instant.now()));
            }
            return validate(workspaceId, request(space)).map(result -> new MemoryVectorSpaceStatus(
                    space.getModelKey(),
                    space.getTableName(),
                    space.getStatus(),
                    result.valid(),
                    result.reasonCode(),
                    Instant.now()));
        });
    }

    public Mono<MemoryVectorSpaceSyncResult> sync() {
        return capabilityProbe.probe().flatMap(capability -> spaces
                .findAll()
                .collectList()
                .flatMap(entries -> {
                    var available = new AtomicInteger();
                    var disabled = new AtomicInteger();
                    return Flux.fromIterable(entries)
                            .concatMap(space -> syncSpace(space, capability, available, disabled))
                            .then(Mono.fromSupplier(() -> new MemoryVectorSpaceSyncResult(
                                    entries.size(),
                                    available.get(),
                                    disabled.get(),
                                    capability.reasonCode())));
                }));
    }

    private Mono<Void> syncSpace(
            MemoryVectorSpaceEntity space,
            PgVectorCapability capability,
            AtomicInteger available,
            AtomicInteger disabled) {
        if (DISABLED.equals(space.getStatus())) {
            disabled.incrementAndGet();
            return Mono.empty();
        }
        return usable(space, capability).flatMap(usable -> {
            var status = usable ? ACTIVE : MISSING;
            if (usable) {
                available.incrementAndGet();
            } else {
                disabled.incrementAndGet();
            }
            if (status.equals(space.getStatus())) {
                return Mono.empty();
            }
            return spaces.updateStatus(space.getId(), status, Instant.now());
        });
    }

    private Mono<Boolean> usable(
            MemoryVectorSpaceEntity space, PgVectorCapability capability) {
        if (!capability.usable()) {
            return Mono.just(false);
        }
        return models
                .findById(space.getEmbeddingModelId())
                .filter(model -> ACTIVE.equals(model.status())
                        && model.capabilities() != null
                        && model.capabilities().containsKey("embedding"))
                .flatMap(model -> physical(request(space)))
                .map(MemoryVectorSpaceValidationResult::valid)
                .defaultIfEmpty(false);
    }

    private Mono<MemoryVectorSpaceValidationResult> physical(
            MemoryVectorSpaceCreateRequest request) {
        var expectedColumn = expectedColumn(request);
        return spaces.tableShape(request.tableName())
                .map(shape -> {
                    if (!expectedColumn.equalsIgnoreCase(shape.columnType())) {
                        return Mono.just(new MemoryVectorSpaceValidationResult(
                                false,
                                COLUMN_MISMATCH,
                                request.tableName(),
                                expectedColumn,
                                shape.columnType(),
                                null,
                                null));
                    }
                    return index(request, expectedColumn, shape.columnType());
                })
                .defaultIfEmpty(Mono.just(new MemoryVectorSpaceValidationResult(
                        false,
                        TABLE_MISSING,
                        request.tableName(),
                        expectedColumn,
                        null,
                        null,
                        null)))
                .flatMap(mono -> mono);
    }

    private Mono<MemoryVectorSpaceValidationResult> index(
            MemoryVectorSpaceCreateRequest request,
            String expectedColumn,
            String actualColumn) {
        var expectedOpclass = VECTOR.equals(request.storageType())
                ? "vector_cosine_ops"
                : "halfvec_cosine_ops";
        return spaces.indexShape(request.tableName(), request.hnswIndexName())
                .map(shape -> {
                    var valid = HNSW.equalsIgnoreCase(shape.indexMethod())
                            && EMBEDDING_COLUMN.equalsIgnoreCase(shape.columnName())
                            && expectedOpclass.equalsIgnoreCase(shape.opclass());
                    return new MemoryVectorSpaceValidationResult(
                            valid,
                            valid ? READY : INDEX_MISMATCH,
                            request.tableName(),
                            expectedColumn,
                            actualColumn,
                            shape.indexMethod(),
                            shape.opclass());
                })
                .defaultIfEmpty(new MemoryVectorSpaceValidationResult(
                        false,
                        INDEX_MISMATCH,
                        request.tableName(),
                        expectedColumn,
                        actualColumn,
                        null,
                        null));
    }

    private Mono<MemoryVectorSpaceEntity> require(String modelKey) {
        return spaces
                .findByModelKey(normalizedModelKey(modelKey))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "memory-vector-space-not-found",
                        "向量空间不存在。")));
    }

    private static MemoryVectorSpaceCreateRequest normalize(
            MemoryVectorSpaceCreateRequest request) {
        if (request == null || request.embeddingModelId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-vector-embedding-model-required",
                    "Embedding 模型标识不能为空。");
        }
        var modelKey = normalizedModelKey(request.modelKey());
        var tableName = normalizedTableName(request.tableName());
        var storageType = normalized(request.storageType());
        if (!VECTOR.equals(storageType) && !HALFVEC.equals(storageType)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, STORAGE_TYPE_INVALID, "存储类型必须是 vector 或 halfvec。");
        }
        if (request.dimension() == null || request.dimension() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, DIMENSION_INVALID, "向量维度必须是正整数。");
        }
        var metric = StringUtils.hasText(request.metric())
                ? normalized(request.metric())
                : COSINE;
        if (!COSINE.equals(metric)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, METRIC_INVALID, "向量距离只支持 cosine。");
        }
        var indexName = normalizedIndexName(request.hnswIndexName());
        return new MemoryVectorSpaceCreateRequest(
                request.embeddingModelId(),
                modelKey,
                tableName,
                storageType,
                request.dimension(),
                metric,
                indexName);
    }

    private static String normalizedModelKey(String value) {
        var normalized = normalized(value);
        if (!normalized.matches("^[a-z0-9_]{1,40}$")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    MODEL_KEY_INVALID,
                    "模型键必须由 1 到 40 位小写字母、数字或下划线组成。");
        }
        return normalized;
    }

    private static String normalizedTableName(String value) {
        var normalized = normalized(value);
        if (!normalized.matches("^memory_vectors_[a-z0-9_]{1,40}$")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TABLE_NAME_INVALID,
                    "向量表名必须以 memory_vectors_ 开头并使用小写字母、数字或下划线。");
        }
        return normalized;
    }

    private static String normalizedIndexName(String value) {
        var normalized = normalized(value);
        if (normalized.length() > 128) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, INDEX_NAME_INVALID, "HNSW 索引名长度不能超过 128。");
        }
        return normalized;
    }

    private static String normalized(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-vector-request-invalid",
                    "向量空间字段不能为空。");
        }
        return value.trim();
    }

    private static String expectedColumn(MemoryVectorSpaceCreateRequest request) {
        return request.storageType() + "(" + request.dimension() + ")";
    }

    private static MemoryVectorSpaceEntity entity(MemoryVectorSpaceCreateRequest request) {
        var now = Instant.now();
        var entity = new MemoryVectorSpaceEntity();
        entity.setId(UUID.randomUUID());
        entity.setEmbeddingModelId(request.embeddingModelId());
        entity.setModelKey(request.modelKey());
        entity.setTableName(request.tableName());
        entity.setStorageType(request.storageType());
        entity.setDimension(request.dimension());
        entity.setMetric(request.metric());
        entity.setHnswIndexName(request.hnswIndexName());
        entity.setStatus(ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static MemoryVectorSpaceCreateRequest request(MemoryVectorSpaceEntity space) {
        return new MemoryVectorSpaceCreateRequest(
                space.getEmbeddingModelId(),
                space.getModelKey(),
                space.getTableName(),
                space.getStorageType(),
                space.getDimension(),
                space.getMetric(),
                space.getHnswIndexName());
    }

    private static MemoryVectorSpaceValidationResult invalid(
            MemoryVectorSpaceCreateRequest request, String reasonCode,
            String expectedColumn, String actualColumn) {
        return new MemoryVectorSpaceValidationResult(
                false,
                reasonCode,
                request.tableName(),
                expectedColumn,
                actualColumn,
                null,
                null);
    }

    private static ApiException invalid(MemoryVectorSpaceValidationResult result) {
        var status = switch (result.reasonCode()) {
            case MODEL_UNAVAILABLE, PGVECTOR_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TABLE_MISSING, COLUMN_MISMATCH, INDEX_MISMATCH -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return new ApiException(status, result.reasonCode(), "向量空间校验失败。");
    }
}
