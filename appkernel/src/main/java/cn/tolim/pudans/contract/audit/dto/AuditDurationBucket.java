package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit record count for one request duration bucket.")
public record AuditDurationBucket(String bucket, long records) {}
