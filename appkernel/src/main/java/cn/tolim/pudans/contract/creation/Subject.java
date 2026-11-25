package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Subject(
        UUID id,
        String ownerId,
        String workspaceId,
        String name,
        String description,
        String kind,
        UUID avatarAssetId,
        List<String> tags,
        Map<String, Object> metadata,
        String status,
        String visibility,
        Instant createdAt,
        Instant updatedAt) {}
