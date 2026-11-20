import { useMemo, useState } from "react";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Eye, EyeOff, Loader2, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

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
import { UsageCompositionBar } from "../components/usage-composition-bar";
import { UsageDateRangeFilter } from "../components/usage-date-range";
import { UsageKpiCards } from "../components/usage-kpi-cards";
import { UsageRefreshToggle } from "../components/usage-refresh-toggle";
import { UsageScopeToggle } from "../components/usage-scope-toggle";
import { UsageErrorState, UsagePageNoPermission } from "../components/usage-state";
import { UsageTokenChart, type UsageTokenChartMode } from "../components/usage-token-chart";
import { useScopeChange, useUsageAdminAccess, useUsageScopeFallback } from "../lib/admin";
import { fetchUsageOverview, resolveUsageApiError } from "../lib/api";
import type { UsageBucket } from "../lib/api";
import { buildUsageTotalsKpis, usageExportFilename } from "../lib/model";
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
