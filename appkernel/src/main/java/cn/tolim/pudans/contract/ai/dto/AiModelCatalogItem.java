package cn.tolim.pudans.contract.ai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiModelCatalogItem(
        UUID id,
        String logicalName,
        String displayName,
        String description,
        List<String> capabilities,
        Integer contextWindow,
        Integer maxOutputTokens,
        Map<String, Object> parameterSchema) {}
