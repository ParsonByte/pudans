package cn.tolim.pudans.contract.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One user ranked by recent audit activity.")
public record ActiveUser(String name, long operations) {}
