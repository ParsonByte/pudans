package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiMemoryCatalog(
        List<AiCatalogItem> types,
        List<AiCatalogItem> layers) {}
