package cn.tolim.pudans.contract.ai.dto;

import java.util.Map;

public record AiModelUpdateRequest(
        String logicalName,
        String displayName,
        String description,
        Map<String, Object> capabilities,
        Integer contextWindow,
        Integer maxOutputTokens,
        Map<String, Object> parameterSchema,
        String status) {}
