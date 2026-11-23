package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risk event aggregation for one affected subject.")
public record RiskSubjectStat(String subject, long events, long blocked, long alerts, long scenes) {}
