package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Work;
import java.time.Instant;
import java.util.List;

public record ExploreFeed(
        List<Work> items,
        String nextCursor,
        int limit,
        String emptyState,
        Instant generatedAt) {}
