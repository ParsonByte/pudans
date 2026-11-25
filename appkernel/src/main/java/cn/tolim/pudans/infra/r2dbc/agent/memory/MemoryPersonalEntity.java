package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class MemoryPersonalEntity {

    private UUID id;
    private String ownerId;
    private String workspaceId;
    private String layer;
    private String memoryType;
    private String canonicalKey;
    private String content;
    private String summary;
    private List<Map<String, Object>> evidence = List.of();
    private List<String> sourceIds = List.of();
    private BigDecimal confidence = BigDecimal.ZERO;
    private String status;
    private Instant validFrom;
    private Instant validTo;
    private String contentHash;
    private Long simhash;
    private UUID canonicalId;
    private UUID supersededBy;
    private Instant createdAt;
    private Instant updatedAt;

    public MemoryItem toContract() {
        return new MemoryItem(
                id,
                ownerId,
                workspaceId,
                layer,
                memoryType,
                canonicalKey,
                content,
                summary,
                evidence,
                sourceIds,
                confidence,
                status,
                validFrom,
                validTo,
                contentHash,
                simhash,
                canonicalId,
                supersededBy,
                createdAt,
                updatedAt);
    }
}
