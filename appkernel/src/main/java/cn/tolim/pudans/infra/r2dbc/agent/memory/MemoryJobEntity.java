package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryJob;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class MemoryJobEntity {

    private UUID id;
    private String jobType;
    private String scopeType;
    private String scopeId;
    private Instant watermarkFrom;
    private Instant watermarkTo;
    private String status;
    private int totalItems;
    private int processedItems;
    private int failedItems;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorCode;
    private String errorMessage;

    public MemoryJob toContract() {
        return new MemoryJob(
                id,
                jobType,
                scopeType,
                scopeId,
                watermarkFrom,
                watermarkTo,
                status,
                totalItems,
                processedItems,
                failedItems,
                idempotencyKey,
                createdAt,
                startedAt,
                finishedAt,
                errorCode,
                errorMessage);
    }
}
