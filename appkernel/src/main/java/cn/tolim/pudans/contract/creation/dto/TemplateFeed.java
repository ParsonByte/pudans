package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Template;
import java.time.Instant;
import java.util.List;

public record TemplateFeed(
        List<Template> items,
        String nextCursor,
        int limit,
        String emptyState,
        Instant generatedAt) {}
