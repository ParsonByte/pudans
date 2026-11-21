import { apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConsolePage } from "@/modules/workspace/console/components/console-page";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";
import { fetchRiskEventTrend } from "@/modules/workspace/console/risk/lib/api";

import { ActiveUserRanking } from "./components/active-user-ranking";
import { ActivityOverviewChart } from "./components/activity-overview-chart";
import { LoginTrend } from "./components/login-trend";
import { RecentOperations } from "./components/recent-operations";
import { RoleDistribution } from "./components/role-distribution";
import { StatGrid } from "./components/stat-grid";

const PAGE_DESCRIPTION = "工作区运行状态、活跃用户与最近审计活动。";

export function ConsoleOverviewView() {
  const overviewQuery = useQuery({
    queryKey: ["console", "overview"],
    queryFn: async () => (await apiClient.dashboard.getDashboardOverview()).data,
    retry: 1,
    staleTime: 30_000,
  });
  const riskEventTrendQuery = useQuery({
    queryKey: ["risk-event-trend", 7],
    queryFn: () => fetchRiskEventTrend(7),
    retry: false,
    staleTime: 30_000,
  });

  const pageActions = (
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={() => void overviewQuery.refetch()}
      disabled={overviewQuery.isFetching}
    >
      {overviewQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
      刷新
    </Button>
  );

  if (overviewQuery.isPending) {
    return (
      <ConsolePage title="面板首页" description={PAGE_DESCRIPTION} actions={pageActions}>
        <OverviewSkeleton />
      </ConsolePage>
    );
  }

  if (!overviewQuery.data) {
    return (
      <ConsolePage title="面板首页" description={PAGE_DESCRIPTION} actions={pageActions}>
        <ConsoleState
          wrapper
          variant="error"
          title="概览加载失败"
          description="请检查网络或登录状态后重试。"
          onRetry={() => void overviewQuery.refetch()}
        />
      </ConsolePage>
    );
  }

  const overview = overviewQuery.data;

  return (
    <ConsolePage title="面板首页" description={PAGE_DESCRIPTION} actions={pageActions}>
      <Tabs defaultValue="overview" className="gap-4">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="audit">审计</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="space-y-6">
          <StatGrid overview={overview} />
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
            <ActivityOverviewChart points={overview.operationTrend ?? []} />
            <ActiveUserRanking users={overview.activeUsers ?? []} />
          </div>
          <RoleDistribution roles={overview.roleDistribution ?? []} />
        </TabsContent>
        <TabsContent value="audit" className="space-y-6">
          <LoginTrend
            points={overview.loginTrend ?? []}
            operationTrend={overview.operationTrend ?? []}
            riskEventTrend={riskEventTrendQuery.data ?? []}
            webhookTrend={overview.webhookTrend ?? []}
          />
          <RecentOperations operations={overview.recentAuditLogs ?? []} />
        </TabsContent>
      </Tabs>
    </ConsolePage>
  );
}

function OverviewSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-28" />
        ))}
      </div>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <Skeleton className="h-80" />
        <Skeleton className="h-80" />
      </div>
      <Skeleton className="h-64" />
    </div>
  );
}
