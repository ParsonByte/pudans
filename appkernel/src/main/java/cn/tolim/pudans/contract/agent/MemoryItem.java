package cn.tolim.pudans.contract.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MemoryItem(
        UUID id,
        String ownerId,
        String workspaceId,
        String layer,
        String memoryType,
        String canonicalKey,
        String content,
        String summary,
        List<Map<String, Object>> evidence,
        List<String> sourceIds,
        BigDecimal confidence,
        String status,
        Instant validFrom,
        Instant validTo,
        String contentHash,
        Long simhash,
        UUID canonicalId,
        UUID supersededBy,
        Instant createdAt,
        Instant updatedAt) {}
