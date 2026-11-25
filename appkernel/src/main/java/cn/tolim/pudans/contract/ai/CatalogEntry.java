package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CatalogEntry(
        UUID id,
        String catalogKind,
        String entryKey,
        String label,
        String description,
        String parentKey,
        Integer sortOrder,
        boolean enabled,
        Map<String, Object> metadata,
        Instant validFrom,
        Instant validTo,
        Instant createdAt,
        Instant updatedAt) {}
