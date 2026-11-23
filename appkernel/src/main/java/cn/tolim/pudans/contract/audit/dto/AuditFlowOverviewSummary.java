package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Selected-range flow counters; archivedRecords is the all-time archived count.")
public record AuditFlowOverviewSummary(
        long records,
        long flows,
        long activeUsers,
        long failedFlows,
        @Schema(description = "All-time archived audit record count.")
        long archivedRecords,
        long archivedFlows,
        double averageSteps,
        long averageDurationMs,
        long p95DurationMs) {}
