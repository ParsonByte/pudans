package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.List;

public record AssetFacets(
        long total,
        List<AssetFacetBucket> kinds,
        List<AssetFacetBucket> sources,
        String emptyState,
        Instant generatedAt) {}
