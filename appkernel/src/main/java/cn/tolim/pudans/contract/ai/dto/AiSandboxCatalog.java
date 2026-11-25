package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiSandboxCatalog(
        List<AiCatalogItem> providers,
        List<AiCatalogItem> templates,
        List<AiCatalogItem> resourceProfiles) {}
