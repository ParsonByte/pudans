package cn.tolim.pudans.contract.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiQuotaAssignmentUpdateRequest(
        String subjectType,
        String subjectId,
        UUID planId,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant weeklyAnchorAt,
        String status) {}
