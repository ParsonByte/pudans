package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Daily audit counts in the Beijing time zone.")
public record AuditTimelinePoint(String date, long records, long success, long failure, long denied) {}
