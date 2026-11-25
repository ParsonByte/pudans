package cn.tolim.pudans.contract.ai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiDiscoveredModel(
        String upstreamModelKey,
        String displayName,
        String description,
        String modelType,
        String protocol,
        List<String> protocols,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> capabilities,
        List<String> features,
        Map<String, Object> modalities,
        Map<String, Object> parameterSchema,
        Map<String, Object> catalog,
        String source,
        String catalogStatus,
        UUID importedModelId) {}
