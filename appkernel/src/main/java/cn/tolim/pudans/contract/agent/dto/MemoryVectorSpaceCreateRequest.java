package cn.tolim.pudans.contract.agent.dto;

import java.util.UUID;

public record MemoryVectorSpaceCreateRequest(
        UUID embeddingModelId,
        String modelKey,
        String tableName,
        String storageType,
        Integer dimension,
        String metric,
        String hnswIndexName) {}
