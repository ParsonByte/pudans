package cn.tolim.pudans.contract.creation;

import java.time.Instant;
import java.util.UUID;

public record ActivitySubmission(
        UUID id,
        UUID activityId,
        UUID workId,
        String ownerId,
        String workspaceId,
        String status,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {}
