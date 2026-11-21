import { useMemo, useState } from "react";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Eye, EyeOff, Loader2, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { downloadCsv, toCsv } from "@/lib/csv";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";

import { fetchAiSetting, fetchAllProviderModels } from "../../providers/lib/api";
import { UsageAssetStats } from "../components/usage-asset-stats";
import { UsageCompositionBar } from "../components/usage-composition-bar";
import { UsageDateRangeFilter } from "../components/usage-date-range";
import { UsageKpiCards } from "../components/usage-kpi-cards";
import { UsageModelStatsTable } from "../components/usage-model-stats-table";
import { UsageRefreshToggle } from "../components/usage-refresh-toggle";
import { UsageScopeToggle } from "../components/usage-scope-toggle";
import { UsageErrorState, UsagePageNoPermission } from "../components/usage-state";
import { UsageTokenChart, type UsageTokenChartMode } from "../components/usage-token-chart";
import { UsageUnitTrendChart } from "../components/usage-unit-trend-chart";
import { useScopeChange, useUsageAdminAccess, useUsageScopeFallback } from "../lib/admin";
import {
  fetchKnowledgeBaseStats,
  fetchMemoryStats,
  fetchModelStats,
  fetchUnitStats,
  fetchUsageOverview,
  resolveUsageApiError,
} from "../lib/api";
import type { UsageBucket } from "../lib/api";
import { buildUsageTotalsKpis, usageExportFilename, usageStatsDays } from "../lib/model";
import {
  usageRangeFromSearch,
  usageRangeSearch,
  usageScopeFromSearch,
  useUsageUrlSearch,
} from "../lib/url-state";
import { UsageClearDialog } from "./components/usage-clear-dialog";
import { UsagePlanCard } from "./components/usage-plan-card";
import { UsageRankingsTable } from "./components/usage-rankings";

const OVERVIEW_CSV_HEADERS = [
  "date",
  "requests",
  "succeeded",
  "failed",
  "inputTokens",
  "cacheTokens",
  "outputTokens",
  "totalTokens",
  "units",
];

const PAGE_TITLE = "用量概览";

const AUTO_REFRESH_INTERVAL_MS = 5000;

function bucketFromSearch(value: unknown): UsageBucket {
  return value === "hour" || value === "week" ? value : "day";
}

export function AiUsageOverviewView() {
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canExport = allowedOperations.has("export");
  const providersPath = useMenuPath("ai-providers");
  const plansPath = useMenuPath("ai-plans");
  const queryClient = useQueryClient();
  const { search, update } = useUsageUrlSearch();
  const range = useMemo(() => usageRangeFromSearch(search), [search]);
  const bucket = bucketFromSearch(search.bucket);
  const scope = usageScopeFromSearch(search);
  const onScopeChange = useScopeChange(update);
  const { isAdmin, isResolved: adminResolved } = useUsageAdminAccess();
  const effectiveScope = isAdmin ? scope : "self";
  const [chartMode, setChartMode] = useState<UsageTokenChartMode>("area");
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [showRankings, setShowRankings] = useState(true);
  const [clearOpen, setClearOpen] = useState(false);

  const overviewQuery = useQuery({
    queryKey: ["ai-usage-overview", range.from, range.to, bucket, effectiveScope],
    queryFn: () =>
      fetchUsageOverview({
        from: range.from,
        to: range.to,
        bucket,
        scope: effectiveScope,
      }),
    placeholderData: keepPreviousData,
    retry: false,
    enabled: canView,
    refetchInterval: autoRefresh ? AUTO_REFRESH_INTERVAL_MS : false,
  });
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    retry: false,
    staleTime: 5 * 60_000,
    enabled: canView,
  });
  const settingsQuery = useQuery({
    queryKey: ["ai-settings", "ai-setting"],
    queryFn: fetchAiSetting,
    retry: false,
    staleTime: 5 * 60_000,
    enabled: canView,
  });
  const statsDays = usageStatsDays(range.from, range.to);
  const unitStatsQuery = useQuery({
    queryKey: ["ai-usage", "unit-stats", statsDays],
    queryFn: () => fetchUnitStats(statsDays),
    retry: false,
    staleTime: 60_000,
    enabled: canView && isAdmin,
  });
  const modelStatsQuery = useQuery({
    queryKey: ["ai-usage", "model-stats", statsDays],
    queryFn: () => fetchModelStats(statsDays),
    retry: false,
    staleTime: 60_000,
    enabled: canView && isAdmin,
  });
  const memoryStatsQuery = useQuery({
    queryKey: ["ai-usage", "memory-stats"],
    queryFn: fetchMemoryStats,
    retry: false,
    staleTime: 60_000,
    enabled: canView && isAdmin,
  });
  const kbStatsQuery = useQuery({
    queryKey: ["ai-usage", "kb-stats"],
    queryFn: fetchKnowledgeBaseStats,
    retry: false,
    staleTime: 60_000,
    enabled: canView && isAdmin,
  });

  useUsageScopeFallback(scope, isAdmin, adminResolved, onScopeChange, overviewQuery.isError);

  if (!canView) {
    return <UsagePageNoPermission title={PAGE_TITLE} />;
  }

  const overview = overviewQuery.data;
  const hasTokenUsage = Boolean(overview?.series.some((item) => item.totalTokens > 0));
  const compositionSegments = overview
    ? [
        {
          key: "input",
          label: "输入",
          tokens: Math.max(overview.totals.inputTokens, 0),
          color: "var(--chart-2)",
        },
        {
          key: "cache",
          label: "缓存",
          tokens: Math.max(overview.totals.cacheTokens, 0),
          color: "var(--chart-3)",
        },
        {
          key: "output",
          label: "输出",
          tokens: Math.max(overview.totals.outputTokens, 0),
          color: "var(--chart-1)",
        },
      ]
    : [];

  const applyRange = (nextRange: typeof range) => {
    update(usageRangeSearch(nextRange));
  };

  const exportOverview = () => {
    if (!overview) {
      return;
    }
    const rows = overview.series.map((item) => [
      item.day,
      item.requests,
      item.succeeded,
      item.failed,
      item.inputTokens,
      item.cacheTokens,
      item.outputTokens,
      item.totalTokens,
      item.units,
    ]);
    downloadCsv(usageExportFilename("ai-usage-overview"), toCsv(OVERVIEW_CSV_HEADERS, rows));
    toast.success("用量概览已开始下载。");
  };

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
            <p className="text-xs text-muted-foreground">
              汇总请求、缓存、Token、标准 Token 用量与响应性能，按北京时间聚合。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canExport ? (
              <Button type="button" variant="outline" onClick={exportOverview} disabled={!overview}>
                <Download />
                导出 CSV
              </Button>
            ) : null}
            {isAdmin ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => setClearOpen(true)}
                disabled={overviewQuery.isFetching}
              >
                <Trash2 />
                清空用量
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              onClick={() => void overviewQuery.refetch()}
              disabled={overviewQuery.isFetching}
            >
              {overviewQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
        </div>

        <ConsoleSection>
          <div className="flex flex-wrap items-end gap-4 p-5">
            <UsageDateRangeFilter
              value={range}
              onChange={applyRange}
              disabled={overviewQuery.isFetching}
            />
            {isAdmin ? (
              <UsageScopeToggle
                value={scope}
                onChange={onScopeChange}
                disabled={overviewQuery.isFetching}
              />
            ) : null}
            <div className="pb-1">
              <UsageRefreshToggle checked={autoRefresh} onCheckedChange={setAutoRefresh} />
            </div>
          </div>
        </ConsoleSection>

        {overviewQuery.isError && !overview ? (
          <ConsoleSection>
            <UsageErrorState
              title="无法加载用量概览"
              message={resolveUsageApiError(overviewQuery.error)}
              onRetry={() => void overviewQuery.refetch()}
            />
          </ConsoleSection>
        ) : overviewQuery.isPending ? (
          <>
            <UsageKpiCards items={[]} loading skeletonCount={8} />
            <ConsoleSection>
              <div className="p-5">
                <Skeleton className="h-[300px] w-full" />
              </div>
            </ConsoleSection>
          </>
        ) : overview ? (
          <>
            <UsageKpiCards items={buildUsageTotalsKpis(overview.totals)} />

            <ConsoleSection>
              <ConsoleSectionHeader
                title="Token 构成"
                description="输入、缓存与输出 Token 的占比，缓存命中率见上方指标。"
              />
              <div className="p-5">
                <UsageCompositionBar segments={compositionSegments} />
              </div>
            </ConsoleSection>

            <ConsoleSection>
              <ConsoleSectionHeader
                title="Token 用量趋势"
                description="输入、缓存与输出 Token 的分桶堆叠。"
                action={
                  <div className="flex flex-wrap items-center gap-2">
                    <ToggleGroup
                      type="single"
                      variant="outline"
                      size="sm"
                      value={bucket}
                      onValueChange={(value) => {
                        if (value === "hour" || value === "day" || value === "week") {
                          update({ bucket: value === "day" ? undefined : value });
                        }
                      }}
                    >
                      <ToggleGroupItem value="hour">小时</ToggleGroupItem>
                      <ToggleGroupItem value="day">天</ToggleGroupItem>
                      <ToggleGroupItem value="week">周</ToggleGroupItem>
                    </ToggleGroup>
                    <ToggleGroup
                      type="single"
                      variant="outline"
                      size="sm"
                      value={chartMode}
                      onValueChange={(value) => {
                        if (value) {
                          setChartMode(value as UsageTokenChartMode);
                        }
                      }}
                    >
                      <ToggleGroupItem value="area">面积</ToggleGroupItem>
                      <ToggleGroupItem value="bar">柱状</ToggleGroupItem>
                    </ToggleGroup>
                  </div>
                }
              />
              {hasTokenUsage ? (
                <UsageTokenChart series={overview.series} mode={chartMode} bucket={bucket} />
              ) : (
                <ConsoleEmpty>所选时间范围内暂无 Token 用量。</ConsoleEmpty>
              )}
            </ConsoleSection>

            {isAdmin ? (
              <>
                <ConsoleSection>
                  <ConsoleSectionHeader
                    title="模型标准 Token 统计"
                    description="按模型查看标准 Token 的每日消耗，以及各模型能力、状态和媒体产出。"
                    action={
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline">近 {statsDays} 天</Badge>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            void unitStatsQuery.refetch();
                            void modelStatsQuery.refetch();
                          }}
                          disabled={unitStatsQuery.isFetching || modelStatsQuery.isFetching}
                        >
                          {unitStatsQuery.isFetching || modelStatsQuery.isFetching ? (
                            <Loader2 className="animate-spin" />
                          ) : (
                            <RefreshCw />
                          )}
                          刷新
                        </Button>
                      </div>
                    }
                  />
                  {unitStatsQuery.isError ? (
                    <div className="p-5">
                      <UsageErrorState
                        title="无法加载模型 Token 统计"
                        message={resolveUsageApiError(unitStatsQuery.error)}
                        onRetry={() => void unitStatsQuery.refetch()}
                      />
                    </div>
                  ) : unitStatsQuery.isPending ? (
                    <div className="p-5">
                      <Skeleton className="h-[300px] w-full" />
                    </div>
                  ) : (unitStatsQuery.data?.rows.length ?? 0) === 0 ? (
                    <ConsoleEmpty>所选时间范围内没有模型 Token 记录。</ConsoleEmpty>
                  ) : (
                    <UsageUnitTrendChart
                      rows={unitStatsQuery.data?.rows ?? []}
                      models={modelsQuery.data ?? []}
                    />
                  )}

                  <div className="border-t">
                    <ConsoleSectionHeader
                      title="模型能力统计"
                      description="按模型类型、能力与请求状态聚合请求数、标准 Token 和媒体产出。"
                    />
                    {modelStatsQuery.isError ? (
                      <div className="p-5">
                        <UsageErrorState
                          title="无法加载模型能力统计"
                          message="当前账号可能没有模型统计权限，或服务暂不可用。"
                          onRetry={() => void modelStatsQuery.refetch()}
                        />
                      </div>
                    ) : modelStatsQuery.isPending ? (
                      <div className="p-5">
                        <Skeleton className="h-40 w-full" />
                      </div>
                    ) : (modelStatsQuery.data?.rows.length ?? 0) === 0 ? (
                      <ConsoleEmpty>所选时间范围内没有模型请求记录。</ConsoleEmpty>
                    ) : (
                      <UsageModelStatsTable
                        rows={modelStatsQuery.data?.rows ?? []}
                        models={modelsQuery.data ?? []}
                      />
                    )}
                  </div>
                </ConsoleSection>

                <ConsoleSection>
                  <ConsoleSectionHeader
                    title="记忆与知识库资产"
                    description="全站全部记忆层记录与知识库文档、分段和查询总量。"
                    action={<Badge variant="outline">全站</Badge>}
                  />
                  {memoryStatsQuery.isError || kbStatsQuery.isError ? (
                    <div className="p-5">
                      <UsageErrorState
                        title="无法加载记忆与知识库资产"
                        message="当前账号可能没有资产统计权限，或服务暂不可用。"
                        onRetry={() => {
                          void memoryStatsQuery.refetch();
                          void kbStatsQuery.refetch();
                        }}
                      />
                    </div>
                  ) : memoryStatsQuery.isPending || kbStatsQuery.isPending ? (
                    <div className="p-5">
                      <Skeleton className="h-24 w-full" />
                    </div>
                  ) : (
                    <UsageAssetStats
                      memory={memoryStatsQuery.data}
                      knowledgeBase={kbStatsQuery.data}
                    />
                  )}
                </ConsoleSection>
              </>
            ) : null}

            <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
              <ConsoleSection className="overflow-hidden">
                <ConsoleSectionHeader
                  title="用量排行"
                  description="按 Token 占比展示前 10 名，低于 1% 的条目合并为“其他”；模型可查看只读权重系数。"
                  action={
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setShowRankings((current) => !current)}
                    >
                      {showRankings ? <EyeOff /> : <Eye />}
                      {showRankings ? "隐藏排行" : "显示排行"}
                    </Button>
                  }
                />
                {showRankings ? (
                  <UsageRankingsTable
                    rankings={overview.rankings}
                    totalTokens={overview.totals.totalTokens}
                    models={modelsQuery.data ?? []}
                    providersPath={providersPath}
                    plansPath={plansPath}
                    modelsLoading={modelsQuery.isPending}
                    modelsError={modelsQuery.isError}
                    billing={settingsQuery.data?.spec.billing}
                    billingLoading={settingsQuery.isPending}
                    billingError={settingsQuery.isError}
                  />
                ) : (
                  <ConsoleEmpty>排行已隐藏。</ConsoleEmpty>
                )}
              </ConsoleSection>
              <UsagePlanCard />
            </div>
          </>
        ) : null}

        {clearOpen ? (
          <UsageClearDialog
            open={clearOpen}
            range={range}
            onOpenChange={setClearOpen}
            onCleared={() => {
              void queryClient.invalidateQueries({ queryKey: ["ai-usage"] });
            }}
          />
        ) : null}
      </div>
    </ConsolePage>
  );
}
