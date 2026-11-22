    package cn.tolim.pudans.infra.contract.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One period metric with its previous-period value for the dashboard.")
public record DashboardMetric(String key, double value, double previousValue) {}
