import { useMemo, useState } from "react";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Download, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { downloadBlob } from "@/lib/csv";
import { beijingDateKey } from "@/lib/date";
import { cn } from "@/lib/utils";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import {
  formatDuration,
  formatTokenCount,
  requestStatusLabel,
} from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";
import { fetchChatModes } from "@/modules/workspace/index/lib/chat-api";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { UsageCursorPagination } from "../components/usage-cursor-pagination";
import { UsageKpiCards } from "../components/usage-kpi-cards";
import { UsageRefreshToggle } from "../components/usage-refresh-toggle";
import { UsageScopeToggle } from "../components/usage-scope-toggle";
import {
  UsageErrorState,
  UsagePageNoPermission,
  UsageTableSkeleton,
} from "../components/usage-state";
import { useScopeChange, useUsageAdminAccess, useUsageScopeFallback } from "../lib/admin";
import {
  fetchUsageHeatmap,
  fetchUsageRequestExport,
  fetchUsageRequestSample,
  fetchUsageRequests,
  fetchUsageSpeed,
  resolveUsageApiError,
} from "../lib/api";
import type { UsageHeatmapMetric, UsageSpeedGroupBy } from "../lib/api";
import { useCursorPager } from "../lib/cursor";
import {
  buildUsageTotalsKpis,
  formatPercent,
  resolveUsageCacheHitRate,
  usageExportFilename,
} from "../lib/model";
import {
  usageRangeFromSearch,
  usageRangeSearch,
  usageScopeFromSearch,
  useUsageUrlSearch,
} from "../lib/url-state";
import { RequestDetailDialog } from "./components/request-detail-dialog";
import { UsageRequestFilters } from "./components/request-filters";
import { RequestHeatmap } from "./components/request-heatmap";
import { RequestSpeedRanking } from "./components/request-speed-ranking";
import { RequestTrendChart, type UsageTrendChartMode } from "./components/request-trend-chart";
import {
  type UsageTrendMetric,
  type UsageTrendSplit,
  buildUsageTrend,
  deriveUsageSpeed,
  requestPageMedians,
  usageHeatmapWindow,
} from "./lib/analytics";
import {
  type UsageRequestFilterForm,
  createUsageRequestFilterForm,
  isRequestFiltered,
  requestFilterSearch,
  requestFiltersFromSearch,
  toUsageRequestQuery,
} from "./lib/filters";

const PAGE_TITLE = "请求用量";

const TABLE_COLUMNS = 13;

const AUTO_REFRESH_INTERVAL_MS = 5000;

type QuickFilter = "all" | "failed" | "retried";

function quickFilterValue(form: UsageRequestFilterForm): QuickFilter {
  if (form.status === "FAILED" && form.retried !== "true") {
    return "failed";
  }
  if (form.retried === "true") {
    return "retried";
  }
  return "all";
}

function quickFilterPatch(value: QuickFilter): Partial<UsageRequestFilterForm> {
  if (value === "failed") {
    return { status: "FAILED", retried: "" };
  }
  if (value === "retried") {
    return { status: "", retried: "true" };
  }
  return { status: "", retried: "" };
}

function statusBandClass(status: string): string {
  if (status === "FAILED") {
    return "border-l-destructive";
  }
  if (status === "SUCCEEDED" || status === "SUCCESS") {
    return "border-l-emerald-500";
  }
  if (status === "RUNNING") {
    return "border-l-amber-500";
  }
  return "border-l-muted-foreground";
}

export function AiUsageRequestsView() {
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canExport = allowedOperations.has("export");
  const { search, update } = useUsageUrlSearch();
  const range = useMemo(() => usageRangeFromSearch(search), [search]);
  const urlForm = useMemo<UsageRequestFilterForm>(
    () => ({
      ...createUsageRequestFilterForm(),
      range,
      ...requestFiltersFromSearch(search),
    }),
    [range, search],
  );
  const [draft, setDraft] = useState<UsageRequestFilterForm>(() => urlForm);
  const [pageSize, setPageSize] = useState(20);
  const [detailRequestId, setDetailRequestId] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [trendMetric, setTrendMetric] = useState<UsageTrendMetric>("tokens");
  const [trendSplit, setTrendSplit] = useState<UsageTrendSplit>("model");
  const [chartMode, setChartMode] = useState<UsageTrendChartMode>("bar");
  const [speedGroupBy, setSpeedGroupBy] = useState<UsageSpeedGroupBy>("model");
  const [heatmapMetric, setHeatmapMetric] = useState<UsageHeatmapMetric>("requests");
  const scope = usageScopeFromSearch(search);
  const onScopeChange = useScopeChange(update);
  const { isAdmin, isResolved: adminResolved } = useUsageAdminAccess();
  const effectiveScope = isAdmin ? scope : "self";
  const pager = useCursorPager();
  const resetPager = pager.reset;

  const appliedQuery = useMemo(() => toUsageRequestQuery(urlForm), [urlForm]);
  const heatmapWindow = useMemo(() => usageHeatmapWindow(), []);
  const sampleQueryParams = useMemo(
    () => ({ ...appliedQuery, scope: effectiveScope }),
    [appliedQuery, effectiveScope],
  );

  const modesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
    staleTime: 5 * 60_000,
    enabled: canView,
  });
  const requestsQuery = useQuery({
    queryKey: ["ai-usage-requests", appliedQuery, effectiveScope, pager.cursor, pageSize],
    queryFn: () =>
      fetchUsageRequests({ ...appliedQuery, scope: effectiveScope }, pager.cursor, pageSize),
    placeholderData: keepPreviousData,
    retry: false,
    enabled: canView,
    refetchInterval: autoRefresh ? AUTO_REFRESH_INTERVAL_MS : false,
  });
  const sampleQuery = useQuery({
    queryKey: ["ai-usage-request-sample", sampleQueryParams],
    queryFn: () => fetchUsageRequestSample(sampleQueryParams),
    retry: false,
    staleTime: 60_000,
    enabled: canView,
  });
  const speedQuery = useQuery({
    queryKey: [
      "ai-usage-speed",
      urlForm.range.from,
      urlForm.range.to,
      speedGroupBy,
      effectiveScope,
    ],
    queryFn: () =>
      fetchUsageSpeed({
        from: urlForm.range.from,
        to: urlForm.range.to,
        groupBy: speedGroupBy,
        scope: effectiveScope,
      }),
    retry: false,
    enabled: canView,
  });
  const heatmapQuery = useQuery({
    queryKey: [
      "ai-usage-heatmap",
      heatmapWindow.from,
      heatmapWindow.to,
      heatmapMetric,
      effectiveScope,
    ],
    queryFn: () =>
      fetchUsageHeatmap({
        from: heatmapWindow.from,
        to: heatmapWindow.to,
        metric: heatmapMetric,
        scope: effectiveScope,
      }),
    retry: false,
    enabled: canView,
  });

  useUsageScopeFallback(
    scope,
    isAdmin,
    adminResolved,
    onScopeChange,
    requestsQuery.isError || sampleQuery.isError,
  );

  const modeOptions = useMemo(
    () =>
      (modesQuery.data ?? []).map((mode) => ({
        value: mode.name,
        label: mode.displayName || mode.name,
      })),
    [modesQuery.data],
  );

  const sampleItems = useMemo(() => sampleQuery.data ?? [], [sampleQuery.data]);
  const trendItems = useMemo(
    () =>
      sampleItems.filter((item) => {
        const day = beijingDateKey(item.createdAt);
        return day != null && day >= urlForm.range.from && day <= urlForm.range.to;
      }),
    [sampleItems, urlForm.range.from, urlForm.range.to],
  );
  const trend = useMemo(
    () => buildUsageTrend(trendItems, trendMetric, trendSplit),
    [trendItems, trendMetric, trendSplit],
  );
  const speedItems = useMemo(() => {
    if (speedQuery.data) {
      return speedQuery.data.items;
    }
    return sampleItems.length > 0 ? deriveUsageSpeed(sampleItems, speedGroupBy) : [];
  }, [sampleItems, speedGroupBy, speedQuery.data]);
  const heatmapBuckets = heatmapQuery.data?.buckets ?? [];
  const heatmapSource: "server" | null = heatmapQuery.data ? "server" : null;
  const speedSource: "server" | "sample" | null = speedQuery.data
    ? "server"
    : sampleQuery.data
      ? "sample"
      : null;

  if (!canView) {
    return <UsagePageNoPermission title={PAGE_TITLE} />;
  }

  const items = requestsQuery.data?.items ?? [];
  const nextCursor = requestsQuery.data?.nextCursor ?? null;
  const isFiltered = isRequestFiltered(draft);
  const quickFilter = quickFilterValue(draft);

  const kpis = buildUsageTotalsKpis(requestsQuery.data?.totals, false);
  if (sampleQuery.data) {
    const medians = requestPageMedians(sampleQuery.data);
    kpis.push(
      {
        label: "首 Token 中位",
        value: medians.ttftMs == null ? "—" : formatDuration(medians.ttftMs),
        hint: "按最近样本请求计算，最多 2000 条",
      },
      {
        label: "输出速度中位",
        value: medians.tps == null ? "—" : `${formatNumber(medians.tps, 1)} token/秒`,
        hint: "按最近样本请求计算，最多 2000 条",
      },
    );
  }

  const updateDraft = (patch: Partial<UsageRequestFilterForm>) => {
    setDraft((current) => ({ ...current, ...patch }));
  };

  const applyRange = (nextRange: typeof draft.range) => {
    setDraft((current) => ({ ...current, range: nextRange }));
    update(usageRangeSearch(nextRange));
    resetPager();
  };

  const submitForm = (form: UsageRequestFilterForm) => {
    update({
      ...requestFilterSearch(form),
      ...usageRangeSearch(form.range),
    });
    resetPager();
  };

  const applyFilters = () => {
    submitForm(draft);
  };

  const applyQuickFilter = (value: string) => {
    if (value !== "all" && value !== "failed" && value !== "retried") {
      return;
    }
    const next = { ...draft, ...quickFilterPatch(value) };
    setDraft(next);
    submitForm(next);
  };

  const resetFilters = () => {
    const initial = createUsageRequestFilterForm();
    setDraft(initial);
    submitForm(initial);
  };

  const changePageSize = (size: number) => {
    setPageSize(size);
    resetPager();
  };

  const exportRequests = async () => {
    setExporting(true);
    try {
      const blob = await fetchUsageRequestExport({
        ...appliedQuery,
        scope: effectiveScope,
      });
      downloadBlob(usageExportFilename("ai-usage-requests"), blob);
      toast.success("请求用量 CSV 已开始下载。");
    } catch (error) {
      toast.error(resolveUsageApiError(error));
    } finally {
      setExporting(false);
    }
  };

  const retryAnalytics = () => {
    void sampleQuery.refetch();
    void speedQuery.refetch();
    void heatmapQuery.refetch();
  };

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
            <p className="text-xs text-muted-foreground">
              按创建时间倒序查看每个请求的路由、Token、标准 Token 用量与响应指标。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canExport ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => void exportRequests()}
                disabled={exporting || !requestsQuery.data}
              >
                {exporting ? <Loader2 className="animate-spin" /> : <Download />}
                导出 CSV
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              onClick={() => void requestsQuery.refetch()}
              disabled={requestsQuery.isFetching}
            >
              {requestsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
        </div>

        <ConsoleSection>
          <UsageRequestFilters
            form={draft}
            modeOptions={modeOptions}
            disabled={requestsQuery.isFetching}
            isFiltered={isFiltered}
            onChange={updateDraft}
            onRangeChange={applyRange}
            onSubmit={applyFilters}
            onReset={resetFilters}
          />
          <div className="flex flex-wrap items-end gap-4 border-t px-5 py-4">
            {isAdmin ? (
              <UsageScopeToggle
                value={scope}
                onChange={onScopeChange}
                disabled={requestsQuery.isFetching}
              />
            ) : null}
            <div className="pb-1">
              <UsageRefreshToggle checked={autoRefresh} onCheckedChange={setAutoRefresh} />
            </div>
            <p className="pb-1.5 text-xs text-muted-foreground">
              自动刷新每 5 秒重新加载当前页请求与汇总。
            </p>
          </div>
        </ConsoleSection>

        <UsageKpiCards items={kpis} loading={requestsQuery.isPending} skeletonCount={7} />

        <ConsoleSection>
          <ConsoleSectionHeader
            title="请求趋势"
            description={`基于当前筛选下最近 ${sampleQuery.data?.length ?? 0} 条请求样本聚合，最多 2000 条；点击图表可按日期下钻。`}
            action={
              <div className="flex flex-wrap items-center gap-2">
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={trendMetric}
                  onValueChange={(value) => {
                    if (
                      value === "tokens" ||
                      value === "units" ||
                      value === "calls" ||
                      value === "speed"
                    ) {
                      setTrendMetric(value);
                    }
                  }}
                >
                  <ToggleGroupItem value="tokens">Token</ToggleGroupItem>
                  <ToggleGroupItem value="units">标准 Token</ToggleGroupItem>
                  <ToggleGroupItem value="calls">请求</ToggleGroupItem>
                  <ToggleGroupItem value="speed">速度</ToggleGroupItem>
                </ToggleGroup>
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={trendSplit}
                  onValueChange={(value) => {
                    if (value === "model" || value === "provider" || value === "agent") {
                      setTrendSplit(value);
                    }
                  }}
                >
                  <ToggleGroupItem value="model">模型</ToggleGroupItem>
                  <ToggleGroupItem value="provider">供应商</ToggleGroupItem>
                  <ToggleGroupItem value="agent">Agent</ToggleGroupItem>
                </ToggleGroup>
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={chartMode}
                  onValueChange={(value) => {
                    if (value === "bar" || value === "line") {
                      setChartMode(value);
                    }
                  }}
                >
                  <ToggleGroupItem value="bar">柱状</ToggleGroupItem>
                  <ToggleGroupItem value="line">折线</ToggleGroupItem>
                </ToggleGroup>
              </div>
            }
          />
          {sampleQuery.isError ? (
            <UsageErrorState
              title="无法加载请求趋势"
              message={resolveUsageApiError(sampleQuery.error)}
              onRetry={retryAnalytics}
            />
          ) : sampleQuery.isPending ? (
            <div className="p-5">
              <Skeleton className="h-[320px] w-full" />
            </div>
          ) : trend.points.length === 0 ? (
            <ConsoleEmpty>当前筛选条件下没有可聚合的请求样本。</ConsoleEmpty>
          ) : (
            <RequestTrendChart
              trend={trend}
              metric={trendMetric}
              mode={chartMode}
              onSelectDay={(day) => applyRange({ preset: "custom", from: day, to: day })}
            />
          )}
        </ConsoleSection>

        <div className="grid gap-4 xl:grid-cols-2">
          <ConsoleSection className="overflow-hidden">
            <ConsoleSectionHeader title="速度排行" />
            <RequestSpeedRanking
              items={speedItems}
              groupBy={speedGroupBy}
              onGroupByChange={setSpeedGroupBy}
              loading={speedQuery.isPending || (speedQuery.isError && sampleQuery.isPending)}
              source={speedSource}
              errorMessage={
                speedQuery.isError && sampleQuery.isError
                  ? resolveUsageApiError(speedQuery.error)
                  : undefined
              }
              onRetry={retryAnalytics}
            />
          </ConsoleSection>

          <ConsoleSection className="overflow-hidden">
            <ConsoleSectionHeader title="请求日历" />
            <RequestHeatmap
              from={heatmapWindow.from}
              to={heatmapWindow.to}
              metric={heatmapMetric}
              onMetricChange={setHeatmapMetric}
              buckets={heatmapBuckets}
              loading={heatmapQuery.isPending}
              source={heatmapSource}
              errorMessage={
                heatmapQuery.isError
                  ? `无法加载请求日历：服务端热力图端点不可用或请求失败，未使用推算数据。${resolveUsageApiError(heatmapQuery.error)}`
                  : undefined
              }
              onRetry={() => void heatmapQuery.refetch()}
            />
          </ConsoleSection>
        </div>

        <ConsoleSection className="overflow-hidden">
          <ConsoleSectionHeader
            title="请求明细"
            description="使用游标分页，筛选条件、范围与每页条数会应用到导出。"
            action={
              <div className="flex flex-wrap items-center gap-2">
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={quickFilter}
                  onValueChange={applyQuickFilter}
                >
                  <ToggleGroupItem value="all">全部</ToggleGroupItem>
                  <ToggleGroupItem value="failed">仅失败</ToggleGroupItem>
                  <ToggleGroupItem value="retried">仅重试</ToggleGroupItem>
                </ToggleGroup>
                <Badge variant="secondary">本页 {items.length} 条</Badge>
              </div>
            }
          />
          {requestsQuery.isError ? (
            <UsageErrorState
              title="无法加载请求用量"
              message={resolveUsageApiError(requestsQuery.error)}
              onRetry={() => void requestsQuery.refetch()}
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">时间</TableHead>
                  <TableHead className="px-5">请求 ID</TableHead>
                  <TableHead className="px-5">Agent</TableHead>
                  <TableHead className="px-5">模型</TableHead>
                  <TableHead className="px-5">供应商</TableHead>
                  <TableHead className="px-5">状态</TableHead>
                  <TableHead className="px-5 text-end">Tokens</TableHead>
                  <TableHead className="px-5 text-end">缓存命中</TableHead>
                  <TableHead className="px-5 text-end">标准 Token</TableHead>
                  <TableHead className="px-5 text-end">延迟</TableHead>
                  <TableHead className="px-5 text-end">首 Token</TableHead>
                  <TableHead className="px-5">档位 / 重试</TableHead>
                  <TableHead className="px-5 text-end">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requestsQuery.isPending ? (
                  <UsageTableSkeleton columns={TABLE_COLUMNS} rows={6} />
                ) : items.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={TABLE_COLUMNS}>
                      <ConsoleEmpty>当前筛选条件下没有请求记录。</ConsoleEmpty>
                    </TableCell>
                  </TableRow>
                ) : (
                  items.map((request) => {
                    const cacheRead = request.cacheReadTokens ?? request.cacheTokens;
                    const cacheHit = resolveUsageCacheHitRate({
                      cacheHitRate: request.cacheHitRate,
                      inputTokens: request.inputTokens ?? 0,
                      cacheTokens: cacheRead ?? 0,
                    });
                    return (
                      <TableRow
                        key={request.requestId}
                        className={cn("cursor-pointer border-l-2", statusBandClass(request.status))}
                        onClick={() => setDetailRequestId(request.requestId)}
                      >
                        <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                          {formatUserDateTime(request.createdAt)}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-44 font-mono text-xs">
                            {request.requestId}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <p className="text-sm">{request.agent || "—"}</p>
                          <p className="text-[10px] text-muted-foreground">
                            {request.agentSource || "—"}
                          </p>
                        </TableCell>
                        <TableCell className="px-5 py-3 text-sm">{request.model || "—"}</TableCell>
                        <TableCell className="px-5 py-3 text-sm">
                          {request.provider || "—"}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <StatusBadge status={request.status} errorCode={request.errorCode} />
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                          <p>{formatTokenCount(request.totalTokens ?? undefined)}</p>
                          <p className="text-[10px] text-muted-foreground">
                            读 {formatTokenCount(cacheRead ?? undefined)} · 写{" "}
                            {formatTokenCount(request.cacheWriteTokens ?? undefined)}
                          </p>
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end text-xs tabular-nums">
                          {formatPercent(cacheHit)}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                          {formatTokenCount(request.units ?? undefined)}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                          {request.latencyMs == null ? "—" : formatDuration(request.latencyMs)}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                          {request.ttftMs == null ? "—" : formatDuration(request.ttftMs)}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <p className="text-xs">{request.servedEffort || "—"}</p>
                          {request.retried ? (
                            <Badge variant="outline" className="mt-1">
                              重试
                              {request.retryCount != null && request.retryCount > 0
                                ? ` ×${request.retryCount}`
                                : ""}
                            </Badge>
                          ) : null}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={(event) => {
                              event.stopPropagation();
                              setDetailRequestId(request.requestId);
                            }}
                          >
                            查看
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          )}
        </ConsoleSection>

        <UsageCursorPagination
          page={pager.page}
          pageSize={pageSize}
          hasPrevious={pager.hasPrevious}
          hasNext={nextCursor != null}
          onPrevious={pager.goPrevious}
          onNext={() => pager.goNext(nextCursor)}
          onPageSizeChange={changePageSize}
          disabled={requestsQuery.isFetching}
          className="mt-auto"
        />

        {detailRequestId ? (
          <RequestDetailDialog
            requestId={detailRequestId}
            onClose={() => setDetailRequestId(null)}
          />
        ) : null}
      </div>
    </ConsolePage>
  );
}

function StatusBadge({ status, errorCode }: { status: string; errorCode: string | null }) {
  const variant =
    status === "FAILED" ? "destructive" : status === "RUNNING" ? "outline" : "secondary";
  return (
    <div className="flex flex-col items-start gap-1">
      <Badge variant={variant}>{requestStatusLabel(status)}</Badge>
      {errorCode ? (
        <span className="font-mono text-[10px] text-destructive">{errorCode}</span>
      ) : null}
    </div>
  );
}
