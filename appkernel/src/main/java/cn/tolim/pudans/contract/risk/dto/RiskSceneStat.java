package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risk event aggregation for one rule scene, including configured rules without events.")
public record RiskSceneStat(
        String scene,
        String label,
        String description,
        String action,
        boolean enabled,
        long events,
        long blocked,
        long alerts,
        long subjects) {}
