package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiAssetCatalog(
        List<AiCatalogItem> kinds,
        List<AiCatalogItem> sources) {}
