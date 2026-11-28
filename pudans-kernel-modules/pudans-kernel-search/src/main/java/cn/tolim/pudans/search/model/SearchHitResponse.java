package cn.tolim.pudans.search.model;

import java.time.LocalDateTime;
import java.util.List;

public record SearchHitResponse(
        String id,
        String documentId,
        String title,
        String summary,
        String snippet,
        List<String> tags,
        String ownerId,
        String orgId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}