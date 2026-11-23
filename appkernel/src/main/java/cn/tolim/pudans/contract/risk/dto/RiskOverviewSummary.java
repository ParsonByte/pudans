package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risk event, ban and rule counters for the selected range.")
public record RiskOverviewSummary(
        long events,
        long blocked,
        long alerts,
        long activeBans,
        long affectedSubjects,
        long enabledRules,
        long totalRules) {}
