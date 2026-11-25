package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Template(
        UUID id,
        String ownerId,
        String workspaceId,
        String name,
        String title,
        String description,
        String mediaType,
        String prompt,
        Map<String, Object> settings,
        Map<String, Object> parameterSchema,
        UUID coverAssetId,
        List<String> tags,
        Map<String, Object> metadata,
        String status,
        String visibility,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {}
