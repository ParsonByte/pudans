package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.UUID;

public record Activity(
        UUID id,
        String ownerId,
        String workspaceId,
        String title,
        String description,
        String rules,
        UUID coverAssetId,
        UUID templateId,
        String status,
        String visibility,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt) {}
