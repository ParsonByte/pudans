package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Asset;
import java.time.Instant;
import java.util.List;

public record AssetPage(
        List<Asset> items,
        String nextCursor,
        int limit,
        String emptyState,
        Instant generatedAt) {}
