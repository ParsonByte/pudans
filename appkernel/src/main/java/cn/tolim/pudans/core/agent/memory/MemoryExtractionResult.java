package cn.tolim.pudans.core.agent.memory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MemoryExtractionResult(
        String layer,
        String memoryType,
        String canonicalKey,
        String content,
        String summary,
        BigDecimal confidence,
        List<UUID> evidenceMessageIds) {}
