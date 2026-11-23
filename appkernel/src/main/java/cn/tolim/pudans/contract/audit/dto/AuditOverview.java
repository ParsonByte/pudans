package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Aggregated audit activity for the selected range.")
public record AuditOverview(
        int rangeDays,
        Instant generatedAt,
        AuditOverviewSummary summary,
        List<AuditTimelinePoint> timeline,
        List<AuditHeatmapCell> heatmap,
        List<AuditOperationStat> operations,
        List<AuditResultStat> results,
        List<AuditDurationBucket> durations,
        List<AuditActorStat> actors) {}
