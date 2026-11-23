package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "One recent risk control audit event.")
public record RiskEventView(
        String auditId,
        String scene,
        String subject,
        String operation,
        String result,
        String path,
        long count,
        Instant occurredAt) {}
