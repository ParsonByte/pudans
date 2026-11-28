package cn.tolim.pudans.search.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SearchDocumentResponse(
        String id,
        String appCode,
        String indexCode,
        String documentId,
        String title,
        String summary,
        String content,
        List<String> tags,
        Map<String, Object> metadata,
        String ownerId,
        String orgId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}