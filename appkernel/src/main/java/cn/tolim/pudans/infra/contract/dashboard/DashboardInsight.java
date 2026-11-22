package cn.tolim.pudans.infra.contract.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One security or identity diagnostic item derived from dashboard data.")
public record DashboardInsight(String severity, String title, String description) {}
