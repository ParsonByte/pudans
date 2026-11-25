package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryVectorSpace;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class MemoryVectorSpaceEntity {

    private UUID id;
    private UUID embeddingModelId;
    private String modelKey;
    private String tableName;
    private String storageType;
    private int dimension;
    private String metric;
    private String hnswIndexName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public MemoryVectorSpace toContract() {
        return toContract(status, updatedAt);
    }

    public MemoryVectorSpace toContract(String newStatus, Instant newUpdatedAt) {
        return new MemoryVectorSpace(
                id,
                embeddingModelId,
                modelKey,
                tableName,
                storageType,
                dimension,
                metric,
                hnswIndexName,
                newStatus,
                createdAt,
                newUpdatedAt);
    }
}
