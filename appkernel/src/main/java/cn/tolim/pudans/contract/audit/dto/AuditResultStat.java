package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit record aggregation for one result.")
public record AuditResultStat(String result, long records) {}
