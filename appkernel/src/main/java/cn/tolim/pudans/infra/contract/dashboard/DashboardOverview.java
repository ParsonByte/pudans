package cn.tolim.pudans.infra.contract.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Aggregated dashboard overview with site info and security posture counters.")
public record DashboardOverview(
        GlobalInfoSummary globalInfo,
        int rangeDays,
        Instant generatedAt,
        long totalUsers,
        long totalRoles,
        long totalUserGroups,
        long totalRiskRules,
        long totalWebhooks,
        long todayOperations,
        List<RecentOperation> recentAuditLogs,
        List<LoginTrendPoint> loginTrend,
        List<LoginTrendPoint> operationTrend,
        List<LoginTrendPoint> failureTrend,
        List<LoginTrendPoint> riskRuleTrend,
        List<LoginTrendPoint> webhookTrend,
        List<ActiveUser> activeUsers,
        List<RoleUserCount> roleDistribution,
        List<ResultCount> resultDistribution,
        List<OperationCount> topOperations,
        List<DashboardMetric> metrics,
        List<DashboardInsight> insights) {}
