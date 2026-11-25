package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiExploreCatalog(
        List<AiCatalogItem> categories,
        List<AiCatalogItem> filters) {}
