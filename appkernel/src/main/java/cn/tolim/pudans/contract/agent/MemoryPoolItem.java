package cn.tolim.pudans.contract.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MemoryPoolItem(
        UUID id,
        String layer,
        String memoryType,
        String title,
        String summary,
        String generalizedContent,
        int supportCount,
        int sourceOwnerCount,
        int sourceWorkspaceCount,
        String safetyClass,
        BigDecimal confidence,
        String status,
        Instant validFrom,
        Instant validTo,
        String contentHash,
        Long simhash,
        UUID canonicalId,
        UUID supersededBy,
        String reviewerId,
        Instant reviewedAt,
        Instant nextReviewAt,
        Instant createdAt,
        Instant updatedAt) {}
