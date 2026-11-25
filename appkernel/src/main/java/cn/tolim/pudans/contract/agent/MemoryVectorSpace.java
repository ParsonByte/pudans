package cn.tolim.pudans.contract.agent;

import java.time.Instant;
import java.util.UUID;

public record MemoryVectorSpace(
        UUID id,
        UUID embeddingModelId,
        String modelKey,
        String tableName,
        String storageType,
        int dimension,
        String metric,
        String hnswIndexName,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
