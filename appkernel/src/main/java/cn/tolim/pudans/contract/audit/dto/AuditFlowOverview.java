package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Aggregated audit trace flow activity for the selected range.")
public record AuditFlowOverview(
        int rangeDays,
        Instant generatedAt,
        AuditFlowOverviewSummary summary,
        List<AuditFlowTimelinePoint> timeline,
        List<AuditHeatmapCell> heatmap,
        List<AuditFlowStepBucket> steps,
        List<AuditDurationBucket> durations,
        List<AuditOperationStat> operations,
        List<AuditResultStat> results,
        List<AuditActorStat> actors) {}
