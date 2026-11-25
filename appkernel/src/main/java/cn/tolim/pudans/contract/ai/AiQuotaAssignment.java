package cn.tolim.pudans.contract.ai;

import java.time.Instant;
import java.util.UUID;

public record AiQuotaAssignment(
        UUID id,
        String subjectType,
        String subjectId,
        UUID planId,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant weeklyAnchorAt,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
