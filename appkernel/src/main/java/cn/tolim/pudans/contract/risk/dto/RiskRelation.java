package cn.tolim.pudans.contract.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Relation between one risk scene and one affected subject.")
public record RiskRelation(String scene, String subject, long events, long blocked) {}
