package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Asset(
        UUID id,
        String ownerId,
        String workspaceId,
        String kind,
        String source,
        String storageFileId,
        UUID generationTaskId,
        UUID parentAssetId,
        String name,
        String description,
        String prompt,
        Map<String, Object> settings,
        String contentType,
        Long size,
        Integer width,
        Integer height,
        Long durationMs,
        List<String> tags,
        Map<String, Object> metadata,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
