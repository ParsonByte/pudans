package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Work(
        UUID id,
        String ownerId,
        String workspaceId,
        String title,
        String description,
        UUID coverAssetId,
        UUID primaryAssetId,
        UUID templateId,
        UUID activityId,
        UUID remixedFromWorkId,
        List<String> tags,
        Map<String, Object> metadata,
        String status,
        String visibility,
        long viewCount,
        long likeCount,
        long favoriteCount,
        long shareCount,
        long remixCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {}
