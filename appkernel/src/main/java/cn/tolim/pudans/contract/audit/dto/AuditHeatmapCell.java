package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit record count for one weekday and hour in the Beijing time zone.")
public record AuditHeatmapCell(int weekday, int hour, long records) {}
