package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit record aggregation for one active user.")
public record AuditActorStat(
        String uid, long records, long failures, long operations, long averageDurationMs) {}
