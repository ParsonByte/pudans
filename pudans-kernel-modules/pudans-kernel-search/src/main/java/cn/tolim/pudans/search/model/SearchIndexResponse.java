package cn.tolim.pudans.search.model;

import java.time.LocalDateTime;

public record SearchIndexResponse(
        String id,
        String appCode,
        String indexCode,
        String name,
        String description,
        boolean enabled,
        long documentCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}