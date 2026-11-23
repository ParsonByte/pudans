package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit record aggregation for one operation.")
public record AuditOperationStat(String operation, long records) {}
