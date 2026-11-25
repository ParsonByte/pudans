package cn.tolim.pudans.contract.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record UnderstandingMapEntry(
        String dimension,
        String label,
        String description,
        int memoryCount,
        BigDecimal averageConfidence,
        Instant lastUpdatedAt,
        String emptyState,
        Map<String, Object> metadata) {}
