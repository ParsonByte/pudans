package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.List;

public record AssetCatalogView(
        List<AssetCatalogItem> kinds,
        List<AssetCatalogItem> sources,
        long total,
        String emptyState,
        Instant generatedAt) {}
