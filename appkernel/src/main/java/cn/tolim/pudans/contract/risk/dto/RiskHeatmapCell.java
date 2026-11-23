package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risk event count for one weekday and hour in the Beijing time zone.")
public record RiskHeatmapCell(int weekday, int hour, long events) {}
