package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Daily risk event counts in the Beijing time zone.")
public record RiskTimelinePoint(String date, long events, long blocked, long alerts) {}
