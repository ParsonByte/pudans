package cn.tolim.pudans.contract.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trace flow count for one step-count bucket.")
public record AuditFlowStepBucket(String bucket, long flows) {}
