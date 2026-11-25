package cn.tolim.pudans.contract.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemoryRetrievedItem(
        UUID memoryId,
        String namespace,
        String layer,
        String memoryType,
        String content,
        String summary,
        BigDecimal confidence,
        Instant validFrom,
        Instant validTo,
        double score,
        List<String> sourceIds) {}
