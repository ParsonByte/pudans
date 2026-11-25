package cn.tolim.pudans.contract.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MemoryCreateRequest(
        String layer,
        String memoryType,
        String canonicalKey,
        String content,
        String summary,
        List<Map<String, Object>> evidence,
        List<String> sourceIds,
        BigDecimal confidence,
        Instant validFrom,
        Instant validTo) {}
