package cn.tolim.pudans.contract.agent.dto;

import java.util.List;

public record MemoryRetrieveRequest(
        String query,
        List<String> layers,
        Integer maxItems,
        Integer maxTokens,
        Boolean includeEvidence) {}
