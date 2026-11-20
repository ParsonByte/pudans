import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Download, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { downloadCsv, toCsv } from "@/lib/csv";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { UsageDateRangeFilter } from "../components/usage-date-range";
import { UsageKpiCards } from "../components/usage-kpi-cards";
import { UsageScopeToggle } from "../components/usage-scope-toggle";
import { UsageErrorState, UsagePageNoPermission } from "../components/usage-state";
import { useScopeChange, useUsageAdminAccess, useUsageScopeFallback } from "../lib/admin";
import { fetchUsageContext, fetchUsageContextGrid, resolveUsageApiError } from "../lib/api";
import type { UsageContextBreakdown, UsageContextQuery } from "../lib/api";
import {
  type UsageDateRangeValue,
  type UsageKpiItem,
  formatPercent,
  usageExportFilename,
  usageMedian,
} from "../lib/model";
import {
  usageRangeFromSearch,
  usageRangeSearch,
  usageScopeFromSearch,
  usageTextParam,
  useUsageUrlSearch,
} from "../lib/url-state";
import { RequestDetailDialog } from "../requests/components/request-detail-dialog";
import { ContextEstimationNote, UsageContextComposition } from "./components/context-composition";
import { ContextGrid } from "./components/context-grid";
import { UsageContextRankings } from "./components/context-rankings";
import { ContextRequestList } from "./components/context-request-list";
import { UsageContextWindow } from "./components/context-window";
import { contextHealth } from "./lib/context";

const PAGE_TITLE = "上下文用量";

const CONTEXT_GRID_LIMIT = 400;

const CONTEXT_CSV_HEADERS = [
  "scope",
  "name",
  "requests",
  "contextWindow",
  "inputTokens",
  "outputTokens",
  "systemTokens",
  "historyTokens",
  "memoryTokens",
  "kbTokens",
  "userTokens",
  "windowCapacity",
  "windowUtilization",
];

function contextExportRow(scope: string, entry: UsageContextBreakdown) {
  return [
    scope,
    entry.name,
    entry.requests,
    entry.contextWindow ?? "",
    entry.inputTokens,
    entry.outputTokens,
    entry.systemTokens,
    entry.historyTokens,
    entry.memoryTokens,
    entry.kbTokens,
    entry.userTokens,
    entry.contextWindowTokens ?? "",
    entry.windowUtilization ?? "",
  ];
}

export function AiUsageContextView() {
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canExport = allowedOperations.has("export");
  const { search, update } = useUsageUrlSearch();
  const range = useMemo(() => usageRangeFromSearch(search), [search]);
  const agentParam = usageTextParam(search, "agent");
  const modelFilter = usageTextParam(search, "model");
  const scope = usageScopeFromSearch(search);
  const onScopeChange = useScopeChange(update);
  const { isAdmin, isResolved: adminResolved } = useUsageAdminAccess();
  const effectiveScope = isAdmin ? scope : "self";
  const [agentDraft, setAgentDraft] = useState(agentParam);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [detailRequestId, setDetailRequestId] = useState<string | null>(null);

  const appliedQuery = useMemo<UsageContextQuery>(
    () => ({
      from: range.from,
      to: range.to,
      agent: agentParam || undefined,
    }),
    [agentParam, range.from, range.to],
  );

  const contextQuery = useQuery({
    queryKey: ["ai-usage-context", appliedQuery, effectiveScope],
    queryFn: () => fetchUsageContext({ ...appliedQuery, scope: effectiveScope }),
    retry: false,
    enabled: canView,
  });
  const gridQuery = useQuery({
    queryKey: [
      "ai-usage-context-grid",
      range.from,
      range.to,
      agentParam,
      modelFilter,
      effectiveScope,
    ],
    queryFn: () =>
      fetchUsageContextGrid({
        from: range.from,
        to: range.to,
        agent: agentParam || undefined,
        model: modelFilter || undefined,
        limit: CONTEXT_GRID_LIMIT,
        scope: effectiveScope,
      }),
    retry: false,
    enabled: canView,
  });

  useUsageScopeFallback(scope, isAdmin, adminResolved, onScopeChange, contextQuery.isError);

  const gridItems = useMemo(() => gridQuery.data?.items ?? [], [gridQuery.data]);
  const gridKpis = useMemo<UsageKpiItem[]>(() => {
    if (gridItems.length === 0) {
      return [];
    }
    const critical = gridItems.filter(
      (item) => contextHealth(item.utilization) === "critical",
    ).length;
    const warning = gridItems.filter(
      (item) => contextHealth(item.utilization) === "warning",
    ).length;
    const utilizationValues = gridItems
      .map((item) => item.utilization)
      .filter((value): value is number => value != null && Number.isFinite(value));
    return [
      {
        label: "采样请求",
        value: formatNumber(gridItems.length),
        hint: `最多 ${CONTEXT_GRID_LIMIT} 个请求`,
      },
      {
        label: "利用率中位",
        value: formatPercent(usageMedian(utilizationValues)),
        hint: "仅统计已配置窗口的请求",
      },
      {
        label: "高危请求",
        value: formatNumber(critical),
        hint: "窗口利用率 ≥ 90%",
      },
      {
        label: "关注请求",
        value: formatNumber(warning),
        hint: "窗口利用率 75% - 90%",
      },
    ];
  }, [gridItems]);

  if (!canView) {
    return <UsagePageNoPermission title={PAGE_TITLE} />;
  }

  const context = contextQuery.data;
  const modelOptions = context?.models ?? [];
  const selectedModel = modelFilter
    ? context?.models.find((model) => model.name === modelFilter)
    : undefined;
  const modelMissing = modelFilter !== "" && context != null && selectedModel == null;
  const activeTotals = modelFilter ? selectedModel : context?.totals;
  const scopeLabel = modelFilter ? `模型 ${modelFilter}` : "全部 Agent 与模型";

  const kpiItems: UsageKpiItem[] = activeTotals
    ? [
        { label: "请求数", value: formatNumber(activeTotals.requests) },
        {
          label: "上下文 Token",
          value: formatTokenCount(activeTotals.inputTokens),
          hint: "请求提示输入总量",
        },
        {
          label: "输出 Token",
          value: formatTokenCount(activeTotals.outputTokens),
          hint: "模型生成 Token",
        },
        {
          label: "窗口利用率",
          value: formatPercent(activeTotals.windowUtilization),
          hint: "仅统计已配置窗口的请求",
        },
        {
          label: "窗口容量合计",
          value: formatTokenCount(activeTotals.contextWindowTokens ?? undefined),
          hint:
            selectedModel?.contextWindow != null
              ? `配置窗口 ${formatTokenCount(selectedModel.contextWindow)}`
              : "按已配置窗口的请求累计",
        },
      ]
    : [];

  const isFiltered =
    range.preset !== "7d" || agentDraft.trim() !== "" || agentParam !== "" || modelFilter !== "";

  const applyRange = (nextRange: UsageDateRangeValue) => {
    update(usageRangeSearch(nextRange));
  };

  const applyFilters = () => {
    update({
      agent: agentDraft.trim() || undefined,
      ...usageRangeSearch(range),
    });
  };

  const changeModel = (value: string) => {
    update({ model: value === "all" ? undefined : value });
  };

  const resetFilters = () => {
    setAgentDraft("");
    update({
      agent: undefined,
      model: undefined,
      ...usageRangeSearch(range),
    });
  };

  const retryGrid = () => {
    void gridQuery.refetch();
  };

  const exportContext = () => {
    if (!context) {
      return;
    }
    const rows = modelFilter
      ? context.models
          .filter((model) => model.name === modelFilter)
          .map((model) => contextExportRow("模型", model))
      : [
          ...context.agents.map((agent) => contextExportRow("Agent", agent)),
          ...context.models.map((model) => contextExportRow("模型", model)),
        ];
    if (rows.length === 0) {
      toast.info("当前筛选条件下没有可导出的上下文用量。");
      return;
    }
    downloadCsv(usageExportFilename("ai-usage-context"), toCsv(CONTEXT_CSV_HEADERS, rows));
    toast.success("上下文用量 CSV 已开始下载。");
  };

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
            <p className="text-xs text-muted-foreground">
              拆解上下文 Token 构成，评估窗口利用率与 Agent、模型维度的上下文开销。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canExport ? (
              <Button type="button" variant="outline" onClick={exportContext} disabled={!context}>
                <Download />
                导出 CSV
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void contextQuery.refetch();
                void gridQuery.refetch();
              }}
              disabled={contextQuery.isFetching}
            >
              {contextQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          </div>
        </div>

        <ConsoleSection>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              applyFilters();
            }}
          >
            <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
              <div className="sm:col-span-2">
                <UsageDateRangeFilter
                  value={range}
                  onChange={applyRange}
                  disabled={contextQuery.isFetching}
                  idPrefix="usage-context-range"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-context-agent" className="text-xs text-muted-foreground">
                  Agent
                </Label>
                <Input
                  id="usage-context-agent"
                  value={agentDraft}
                  onChange={(event) => setAgentDraft(event.target.value)}
                  placeholder="Agent 资源名"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={contextQuery.isFetching}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-context-model" className="text-xs text-muted-foreground">
                  模型
                </Label>
                <Select
                  value={modelFilter || "all"}
                  onValueChange={changeModel}
                  disabled={contextQuery.isFetching || modelOptions.length === 0}
                >
                  <SelectTrigger id="usage-context-model" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">全部模型</SelectItem>
                    {modelOptions.map((model) => (
                      <SelectItem key={model.name} value={model.name}>
                        {model.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 px-5 pb-5">
              <Button type="submit" disabled={contextQuery.isFetching}>
                查询
              </Button>
              {isFiltered ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={resetFilters}
                  disabled={contextQuery.isFetching}
                >
                  重置
                </Button>
              ) : null}
            </div>
          </form>
          <div className="flex flex-wrap items-end gap-4 border-t px-5 py-4">
            {isAdmin ? (
              <UsageScopeToggle
                value={scope}
                onChange={onScopeChange}
                disabled={contextQuery.isFetching}
              />
            ) : null}
            <p className="pb-1.5 text-xs text-muted-foreground">
              窗口利用率按输入 Token 占窗口容量计算，输出 Token 不计入窗口。
            </p>
          </div>
        </ConsoleSection>

        {contextQuery.isError ? (
          <ConsoleSection>
            <UsageErrorState
              title="无法加载上下文用量"
              message={resolveUsageApiError(contextQuery.error)}
              onRetry={() => void contextQuery.refetch()}
            />
          </ConsoleSection>
        ) : contextQuery.isPending ? (
          <>
            <UsageKpiCards items={[]} loading skeletonCount={5} />
            <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <ConsoleSection>
                <div className="p-5">
                  <Skeleton className="h-64 w-full" />
                </div>
              </ConsoleSection>
              <ConsoleSection>
                <div className="p-5">
                  <Skeleton className="h-64 w-full" />
                </div>
              </ConsoleSection>
            </div>
          </>
        ) : context ? (
          modelMissing || !activeTotals ? (
            <ConsoleSection>
              <div className="flex flex-col items-center gap-3 p-6 text-center">
                <ConsoleEmpty>模型 {modelFilter} 在当前筛选条件下没有上下文数据。</ConsoleEmpty>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => changeModel("all")}
                >
                  查看全部模型
                </Button>
              </div>
            </ConsoleSection>
          ) : (
            <>
              <UsageKpiCards items={kpiItems} />

              {!gridQuery.isError ? (
                <UsageKpiCards items={gridKpis} loading={gridQuery.isPending} skeletonCount={4} />
              ) : null}

              <ConsoleSection className="overflow-hidden">
                <ConsoleSectionHeader
                  title="逐请求窗口网格"
                  description="20 × 20 共 400 格，每格代表一个请求；颜色表示窗口利用率健康度，悬停查看上下文构成。"
                />
                {gridQuery.isError ? (
                  <ContextGridMethodology onRetry={retryGrid} />
                ) : gridQuery.isPending ? (
                  <div className="space-y-3 p-5">
                    <Skeleton className="h-[320px] w-full max-w-[720px]" />
                  </div>
                ) : gridItems.length === 0 ? (
                  <div className="space-y-3 p-5">
                    <ConsoleEmpty>当前筛选条件下没有逐请求上下文数据。</ConsoleEmpty>
                    <ContextEstimationNote />
                  </div>
                ) : (
                  <ContextGrid
                    items={gridItems}
                    selectedRequestId={selectedRequestId}
                    onSelect={setSelectedRequestId}
                  />
                )}
              </ConsoleSection>

              {!gridQuery.isError && gridItems.length > 0 ? (
                <ConsoleSection className="overflow-hidden">
                  <ConsoleSectionHeader
                    title="逐请求明细"
                    description="点击行可在上方网格中定位；构成条可查看各部分 Token 占比。"
                  />
                  <ContextRequestList
                    items={gridItems}
                    selectedRequestId={selectedRequestId}
                    onSelect={setSelectedRequestId}
                    onOpen={setDetailRequestId}
                  />
                </ConsoleSection>
              ) : null}

              <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
                <UsageContextComposition totals={activeTotals} scopeLabel={scopeLabel} />
                <UsageContextWindow
                  totals={activeTotals}
                  scopeLabel={scopeLabel}
                  contextWindow={selectedModel?.contextWindow ?? null}
                />
              </div>
              <UsageContextRankings
                agents={context.agents}
                models={context.models}
                modelFilter={modelFilter}
              />
            </>
          )
        ) : null}

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

function ContextGridMethodology({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="space-y-3 p-5">
      <p className="text-sm text-muted-foreground">
        服务端尚未提供逐请求上下文网格端点，无法绘制 400
        格窗口图。本页不使用推算或伪造的逐请求数据；
        下方上下文构成、窗口利用率与维度分布仍来自服务端聚合统计。
      </p>
      <ContextEstimationNote />
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        重试
      </Button>
    </div>
  );
}
