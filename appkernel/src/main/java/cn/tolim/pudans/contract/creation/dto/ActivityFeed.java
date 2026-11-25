package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Activity;
import java.time.Instant;
import java.util.List;

public record ActivityFeed(
        List<Activity> items,
        String nextCursor,
        int limit,
        String emptyState,
        Instant generatedAt) {}
