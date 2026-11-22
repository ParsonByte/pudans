package cn.tolim.pudans.infra.contract.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of audit operations for one result in the selected range.")
public record ResultCount(String result, long count) {}
