package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Daily trace flow counts based on the Beijing flow start date.")
public record AuditFlowTimelinePoint(
        String date,
        long flows,
        long records,
        long failedFlows,
        long archivedRecords) {}
