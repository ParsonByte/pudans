package cn.tolim.pudans.infra.api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Daily audit count for one UTC day.")
public record LoginTrendPoint(String date, long count) {}
