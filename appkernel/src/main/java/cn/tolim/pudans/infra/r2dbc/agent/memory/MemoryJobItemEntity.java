package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryJobItem;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class MemoryJobItemEntity {

    private UUID id;
    private UUID jobId;
    private String sourceId;
    private String stage;
    private String status;
    private int attempt;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public MemoryJobItem toContract() {
        return new MemoryJobItem(
                id,
                jobId,
                sourceId,
                stage,
                status,
                attempt,
                errorCode,
                errorMessage,
                createdAt,
                updatedAt);
    }
}
