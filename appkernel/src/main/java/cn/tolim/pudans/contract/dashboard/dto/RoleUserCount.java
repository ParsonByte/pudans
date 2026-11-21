package cn.tolim.pudans.contract.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of accounts holding one role.")
public record RoleUserCount(String role, String displayName, long users) {}
