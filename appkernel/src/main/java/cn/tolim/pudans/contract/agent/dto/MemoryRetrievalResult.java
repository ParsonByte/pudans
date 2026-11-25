package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.MemorySource;
import java.util.List;

public record MemoryRetrievalResult(
        List<MemoryRetrievedItem> items,
        int estimatedTokens,
        boolean vectorUsed,
        List<String> warnings,
        List<MemorySource> evidence) {}
