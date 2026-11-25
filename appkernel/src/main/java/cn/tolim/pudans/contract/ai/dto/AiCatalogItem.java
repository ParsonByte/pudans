package cn.tolim.pudans.contract.ai.dto;

import java.util.Map;

public record AiCatalogItem(
        String key,
        String label,
        String description,
        String parentKey,
        int sortOrder,
        Map<String, Object> metadata) {}
