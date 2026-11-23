package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit volume, outcome, latency and retention counters for the selected range.")
public record AuditOverviewSummary(
        long records,
        long success,
        long failure,
        long denied,
        long activeUsers,
        long averageDurationMs,
        long p95DurationMs,
        long activeRecords,
        long archivedRecords) {}
