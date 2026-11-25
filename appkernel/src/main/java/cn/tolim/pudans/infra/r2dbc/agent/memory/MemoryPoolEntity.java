package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryPoolItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class MemoryPoolEntity {

    private UUID id;
    private String scope;
    private String layer;
    private String memoryType;
    private String title;
    private String content;
    private String summary;
    private String generalizedContent;
    private int supportCount;
    private int sourceOwnerCount;
    private int sourceWorkspaceCount;
    private List<String> sourceWorkspaceIds = List.of();
    private Map<String, Object> evidenceSummary = Map.of();
    private String safetyClass;
    private BigDecimal confidence = BigDecimal.ZERO;
    private String status;
    private String visibility;
    private Instant validFrom;
    private Instant validTo;
    private String contentHash;
    private Long simhash;
    private UUID canonicalId;
    private UUID supersededBy;
    private String reviewerId;
    private Instant reviewedAt;
    private Instant nextReviewAt;
    private Instant createdAt;
    private Instant updatedAt;

    public MemoryPoolItem toContract() {
        return new MemoryPoolItem(
                id,
                layer,
                memoryType,
                title,
                summary,
                generalizedContent,
                supportCount,
                sourceOwnerCount,
                sourceWorkspaceCount,
                safetyClass,
                confidence,
                status,
                validFrom,
                validTo,
                contentHash,
                simhash,
                canonicalId,
                supersededBy,
                reviewerId,
                reviewedAt,
                nextReviewAt,
                createdAt,
                updatedAt);
    }
}
