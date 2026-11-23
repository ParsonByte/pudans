package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Aggregated risk control posture for the selected range.")
public record RiskOverview(
        int rangeDays,
        Instant generatedAt,
        boolean truncated,
        RiskOverviewSummary summary,
        List<RiskTimelinePoint> timeline,
        List<RiskHeatmapCell> heatmap,
        List<RiskSceneStat> scenes,
        List<RiskSubjectStat> subjects,
        List<RiskRelation> relations,
        List<RiskEventView> recentEvents) {}
