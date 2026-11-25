package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemorySource;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class MemorySourceEntity {

    private UUID id;
    private String ownerId;
    private String workspaceId;
    private String sourceKind;
    private String sourceId;
    private UUID sessionId;
    private UUID runId;
    private UUID taskId;
    private UUID messageStartId;
    private UUID messageEndId;
    private Instant occurredAt;
    private String contentSummary;
    private String contentDigest;
    private String status;
    private Map<String, Object> metadata = Map.of();
    private Instant createdAt;

    public MemorySource toContract() {
        return new MemorySource(
                id,
                ownerId,
                workspaceId,
                sourceKind,
                sourceId,
                sessionId,
                runId,
                taskId,
                messageStartId,
                messageEndId,
                occurredAt,
                contentSummary,
                contentDigest,
                status,
                metadata,
                createdAt);
    }
}
