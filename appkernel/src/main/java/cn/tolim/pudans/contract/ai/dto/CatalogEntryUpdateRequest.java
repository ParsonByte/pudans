package cn.tolim.pudans.contract.ai.dto;

import java.time.Instant;
import java.util.Map;

public record CatalogEntryUpdateRequest(
        String catalogKind,
        String entryKey,
        String label,
        String description,
        String parentKey,
        Integer sortOrder,
        Boolean enabled,
        Map<String, Object> metadata,
        Instant validFrom,
        Instant validTo) {}
