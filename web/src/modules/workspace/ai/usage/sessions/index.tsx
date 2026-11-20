import { useMemo, useState } from "react";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Download, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { downloadCsv, toCsv } from "@/lib/csv";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatPoints, formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { UsageCursorPagination } from "../components/usage-cursor-pagination";
import { UsageDateRangeFilter } from "../components/usage-date-range";
import { UsageKpiCards } from "../components/usage-kpi-cards";
import { UsageScopeToggle } from "../components/usage-scope-toggle";
import {
  UsageErrorState,
  UsagePageNoPermission,
  UsageTableSkeleton,
} from "../components/usage-state";
import { useScopeChange, useUsageAdminAccess, useUsageScopeFallback } from "../lib/admin";
import {
  USAGE_SESSION_EXPORT_MAX_ROWS,
  fetchUsageSessions,
  fetchUsageSessionsForExport,
  fetchUsageSessionsStats,
  resolveUsageApiError,
} from "../lib/api";
import { useCursorPager } from "../lib/cursor";
import {
  USAGE_SESSION_STATUS_OPTIONS,
  type UsageDateRangeValue,
  type UsageKpiItem,
  formatSessionDuration,
  sessionStatusLabel,
  usageExportFilename,
} from "../lib/model";
import {
  usageRangeFromSearch,
  usageRangeSearch,
  usageScopeFromSearch,
  useUsageUrlSearch,
} from "../lib/url-state";
import { SessionActivityChart } from "./components/session-activity-chart";
import { SessionDetailDialog } from "./components/session-detail-dialog";
import { SessionHourHeatmap } from "./components/session-hour-heatmap";
import { SessionModelTable } from "./components/session-model-table";
import { SessionTopTable } from "./components/session-top-table";
import { deriveUsageSessionStats } from "./lib/analytics";
import {
  type UsageSessionFilterForm,
  createUsageSessionFilterForm,
  isSessionFiltered,
  sessionFilterSearch,
  sessionFiltersFromSearch,
  toUsageSessionQuery,
} from "./lib/filters";

const PAGE_TITLE = "会话用量";

const TABLE_COLUMNS = 11;

const SESSION_CSV_HEADERS = [
  "conversationId",
  "title",
  "agent",
  "model",
  "status",
  "messageCount",
  "requestCount",
  "inputTokens",
  "cacheTokens",
  "outputTokens",
  "totalTokens",
  "points",
  "durationSeconds",
  "createdAt",
  "lastActivityAt",
];

export function AiUsageSessionsView() {
  const allowedOperations = useAllowedOperations();
  const canView = allowedOperations.has("view");
  const canExport = allowedOperations.has("export");
  const { search, update } = useUsageUrlSearch();
  const range = useMemo(() => usageRangeFromSearch(search), [search]);
  const urlForm = useMemo<UsageSessionFilterForm>(
    () => ({
      ...createUsageSessionFilterForm(),
      range,
      ...sessionFiltersFromSearch(search),
    }),
    [range, search],
  );
  const [draft, setDraft] = useState<UsageSessionFilterForm>(() => urlForm);
  const [pageSize, setPageSize] = useState(20);
  const [detailConversationId, setDetailConversationId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const scope = usageScopeFromSearch(search);
  const onScopeChange = useScopeChange(update);
  const { isAdmin, isResolved: adminResolved } = useUsageAdminAccess();
  const effectiveScope = isAdmin ? scope : "self";
  const pager = useCursorPager();
  const resetPager = pager.reset;
  const appliedQuery = useMemo(() => toUsageSessionQuery(urlForm), [urlForm]);

  const sessionsQuery = useQuery({
    queryKey: ["ai-usage-sessions", appliedQuery, effectiveScope, pager.cursor, pageSize],
    queryFn: () =>
      fetchUsageSessions({ ...appliedQuery, scope: effectiveScope }, pager.cursor, pageSize),
    placeholderData: keepPreviousData,
    retry: false,
    enabled: canView,
  });
  const statsQuery = useQuery({
    queryKey: ["ai-usage-session-stats", appliedQuery, effectiveScope],
    queryFn: () =>
      fetchUsageSessionsStats({
        from: appliedQuery.from,
        to: appliedQuery.to,
        agent: appliedQuery.agent,
        model: appliedQuery.model,
        provider: appliedQuery.provider,
        status: appliedQuery.status,
        routeGroup: appliedQuery.routeGroup,
        scope: effectiveScope,
      }),
    retry: false,
    enabled: canView,
  });
  const fallbackQuery = useQuery({
    queryKey: ["ai-usage-session-fallback", appliedQuery, effectiveScope],
    queryFn: () => fetchUsageSessionsForExport({ ...appliedQuery, scope: effectiveScope }),
    retry: false,
    enabled: canView && statsQuery.isError,
  });

  useUsageScopeFallback(scope, isAdmin, adminResolved, onScopeChange, sessionsQuery.isError);

  const fallbackStats = useMemo(
    () => (fallbackQuery.data ? deriveUsageSessionStats(fallbackQuery.data.items) : undefined),
    [fallbackQuery.data],
  );
  const stats = statsQuery.data ?? fallbackStats;
  const statsSource: "server" | "sample" | null = statsQuery.data
    ? "server"
    : fallbackStats
      ? "sample"
      : null;
  const statsLoading = statsQuery.isPending || (statsQuery.isError && fallbackQuery.isPending);
  const statsError = statsQuery.isError && fallbackQuery.isError;

  if (!canView) {
    return <UsagePageNoPermission title={PAGE_TITLE} />;
  }

  const items = sessionsQuery.data?.items ?? [];
  const nextCursor = sessionsQuery.data?.nextCursor ?? null;
  const isFiltered = isSessionFiltered(draft);
  const sourceHint =
    statsSource === "server"
      ? "服务端统计"
      : statsSource === "sample"
        ? `按最近 ${formatNumber(fallbackQuery.data?.items.length ?? 0)} 个会话估算（最多 ${USAGE_SESSION_EXPORT_MAX_ROWS} 个）`
        : "";
  const kpiItems: UsageKpiItem[] = stats
    ? [
        {
          label: "会话数",
          value: formatNumber(stats.totals.sessions),
          hint: sourceHint,
        },
        {
          label: "消息中位",
          value: formatNumber(stats.totals.medianMessages, 1),
          hint: sourceHint,
        },
        {
          label: "消息 P90",
          value: formatNumber(stats.totals.p90Messages, 1),
          hint: sourceHint,
        },
        {
          label: "Token 总量",
          value: formatTokenCount(stats.totals.tokens),
          hint: sourceHint,
        },
        {
          label: "积分消耗",
          value: formatPoints(stats.totals.points) ?? "0 点",
          hint: sourceHint,
        },
        {
          label: "缓存读 Token",
          value: formatTokenCount(stats.totals.cacheReadTokens),
          hint: sourceHint,
        },
        {
          label: "活跃时间",
          value: formatSessionDuration(stats.totals.activeSeconds),
          hint: statsSource === "server" ? "服务端统计" : "按会话时长累计估算，不同会话可能重叠",
        },
      ]
    : [];

  const updateDraft = (patch: Partial<UsageSessionFilterForm>) => {
    setDraft((current) => ({ ...current, ...patch }));
  };

  const applyRange = (nextRange: UsageDateRangeValue) => {
    setDraft((current) => ({ ...current, range: nextRange }));
    update(usageRangeSearch(nextRange));
    resetPager();
  };

  const submitForm = (form: UsageSessionFilterForm) => {
    update({
      ...sessionFilterSearch(form),
      ...usageRangeSearch(form.range),
    });
    resetPager();
  };

  const applyFilters = () => {
    submitForm(draft);
  };

  const resetFilters = () => {
    const initial = createUsageSessionFilterForm();
    setDraft(initial);
    submitForm(initial);
  };

  const changePageSize = (size: number) => {
    setPageSize(size);
    resetPager();
  };

  const retryStats = () => {
    void statsQuery.refetch();
    void fallbackQuery.refetch();
  };

  const exportSessions = async () => {
    setExporting(true);
    try {
      const result = await fetchUsageSessionsForExport({
        ...appliedQuery,
        scope: effectiveScope,
      });
      if (result.items.length === 0) {
        toast.info("当前筛选条件下没有可导出的会话。");
        return;
      }
      const rows = result.items.map((session) => [
        session.conversationId,
        session.title ?? "",
        session.agent,
        session.model ?? "",
        session.status,
        session.messageCount,
        session.requestCount,
        session.inputTokens,
        session.cacheTokens,
        session.outputTokens,
        session.totalTokens,
        session.points,
        session.durationSeconds,
        session.createdAt,
        session.lastActivityAt,
      ]);
      downloadCsv(usageExportFilename("ai-usage-sessions"), toCsv(SESSION_CSV_HEADERS, rows));
      toast.success(
        result.truncated
          ? `会话数量超过导出上限，已导出前 ${USAGE_SESSION_EXPORT_MAX_ROWS} 条。`
          : "会话用量 CSV 已开始下载。",
      );
    } catch (error) {
      toast.error(resolveUsageApiError(error));
    } finally {
      setExporting(false);
    }
  };

  return (
    <ConsolePage title={PAGE_TITLE}>
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h2 className="text-2xl font-bold tracking-tight">{PAGE_TITLE}</h2>
            <p className="text-xs text-muted-foreground">
              按会话聚合消息、请求、Token 与积分消耗，可查看最近消息。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canExport ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => void exportSessions()}
                disabled={exporting || !sessionsQuery.data}
              >
                {exporting ? <Loader2 className="animate-spin" /> : <Download />}
                导出 CSV
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              onClick={() => void sessionsQuery.refetch()}
              disabled={sessionsQuery.isFetching}
            >
              {sessionsQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
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
                  value={draft.range}
                  onChange={applyRange}
                  disabled={sessionsQuery.isFetching}
                  idPrefix="usage-session-range"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-session-agent" className="text-xs text-muted-foreground">
                  Agent
                </Label>
                <Input
                  id="usage-session-agent"
                  value={draft.agent}
                  onChange={(event) => updateDraft({ agent: event.target.value })}
                  placeholder="Agent 资源名"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={sessionsQuery.isFetching}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-session-search" className="text-xs text-muted-foreground">
                  搜索
                </Label>
                <Input
                  id="usage-session-search"
                  value={draft.q}
                  onChange={(event) => updateDraft({ q: event.target.value })}
                  placeholder="会话标题或会话键"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={sessionsQuery.isFetching}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-session-provider" className="text-xs text-muted-foreground">
                  供应商
                </Label>
                <Input
                  id="usage-session-provider"
                  value={draft.provider}
                  onChange={(event) => updateDraft({ provider: event.target.value })}
                  placeholder="留空表示全部"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={sessionsQuery.isFetching}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-session-model" className="text-xs text-muted-foreground">
                  模型
                </Label>
                <Input
                  id="usage-session-model"
                  value={draft.model}
                  onChange={(event) => updateDraft({ model: event.target.value })}
                  placeholder="留空表示全部"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={sessionsQuery.isFetching}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="usage-session-status" className="text-xs text-muted-foreground">
                  状态
                </Label>
                <Select
                  value={draft.status || "all"}
                  onValueChange={(value) => updateDraft({ status: value === "all" ? "" : value })}
                  disabled={sessionsQuery.isFetching}
                >
                  <SelectTrigger id="usage-session-status" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">全部状态</SelectItem>
                    {USAGE_SESSION_STATUS_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label
                  htmlFor="usage-session-route-group"
                  className="text-xs text-muted-foreground"
                >
                  路由组
                </Label>
                <Input
                  id="usage-session-route-group"
                  value={draft.routeGroup}
                  onChange={(event) => updateDraft({ routeGroup: event.target.value })}
                  placeholder="留空表示全部"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={sessionsQuery.isFetching}
                />
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 px-5 pb-5">
              <Button type="submit" disabled={sessionsQuery.isFetching}>
                查询
              </Button>
              {isFiltered ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={resetFilters}
                  disabled={sessionsQuery.isFetching}
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
                disabled={sessionsQuery.isFetching}
              />
            ) : null}
            <p className="pb-1.5 text-xs text-muted-foreground">
              统计口径为服务端聚合；统计端点不可用时按最近会话样本估算。
            </p>
          </div>
        </ConsoleSection>

        {statsError ? (
          <ConsoleSection>
            <UsageErrorState
              title="无法加载会话统计"
              message={resolveUsageApiError(statsQuery.error ?? fallbackQuery.error)}
              onRetry={retryStats}
            />
          </ConsoleSection>
        ) : (
          <>
            <UsageKpiCards items={kpiItems} loading={statsLoading} skeletonCount={7} />

            <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
              <ConsoleSection className="overflow-hidden">
                <ConsoleSectionHeader
                  title="每日会话活跃"
                  description="按最后活动日期聚合的会话数与消息数。"
                />
                {statsLoading ? (
                  <div className="p-5">
                    <Skeleton className="h-[280px] w-full" />
                  </div>
                ) : stats && stats.series.length > 0 ? (
                  <SessionActivityChart series={stats.series} />
                ) : (
                  <ConsoleEmpty>所选时间范围内没有会话活跃记录。</ConsoleEmpty>
                )}
              </ConsoleSection>

              <ConsoleSection>
                <ConsoleSectionHeader
                  title="24 小时活跃"
                  description="按最后活动小时分布，颜色越深会话越多。"
                />
                {statsLoading ? (
                  <div className="p-5">
                    <Skeleton className="h-24 w-full" />
                  </div>
                ) : stats ? (
                  <SessionHourHeatmap hours={stats.hours} />
                ) : null}
              </ConsoleSection>
            </div>

            <div className="grid gap-4 xl:grid-cols-2">
              <ConsoleSection className="overflow-hidden">
                <ConsoleSectionHeader
                  title="Top 会话"
                  description="按 Token 消耗排序的前 8 个会话。"
                />
                {statsLoading ? (
                  <div className="space-y-2 p-5">
                    {Array.from({ length: 5 }, (_, index) => (
                      <Skeleton key={index} className="h-10 w-full" />
                    ))}
                  </div>
                ) : stats ? (
                  <SessionTopTable sessions={stats.topSessions} onOpen={setDetailConversationId} />
                ) : null}
              </ConsoleSection>

              <ConsoleSection className="overflow-hidden">
                <ConsoleSectionHeader
                  title="模型分布"
                  description="按会话使用的模型聚合 Token 与积分。"
                />
                {statsLoading ? (
                  <div className="space-y-2 p-5">
                    {Array.from({ length: 5 }, (_, index) => (
                      <Skeleton key={index} className="h-10 w-full" />
                    ))}
                  </div>
                ) : stats ? (
                  <SessionModelTable models={stats.models} />
                ) : null}
              </ConsoleSection>
            </div>
          </>
        )}

        <ConsoleSection className="overflow-hidden">
          <ConsoleSectionHeader
            title="会话明细"
            description="按最后活动时间倒序分页，筛选条件会应用到导出。"
            action={<Badge variant="secondary">本页 {items.length} 条</Badge>}
          />
          {sessionsQuery.isError ? (
            <UsageErrorState
              title="无法加载会话用量"
              message={resolveUsageApiError(sessionsQuery.error)}
              onRetry={() => void sessionsQuery.refetch()}
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">标题</TableHead>
                  <TableHead className="px-5">Agent</TableHead>
                  <TableHead className="px-5">模型</TableHead>
                  <TableHead className="px-5">状态</TableHead>
                  <TableHead className="px-5 text-end">消息数</TableHead>
                  <TableHead className="px-5 text-end">请求数</TableHead>
                  <TableHead className="px-5 text-end">Tokens</TableHead>
                  <TableHead className="px-5 text-end">积分</TableHead>
                  <TableHead className="px-5 text-end">时长</TableHead>
                  <TableHead className="px-5">最后活动</TableHead>
                  <TableHead className="px-5 text-end">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sessionsQuery.isPending ? (
                  <UsageTableSkeleton columns={TABLE_COLUMNS} rows={6} />
                ) : items.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={TABLE_COLUMNS}>
                      <ConsoleEmpty>当前筛选条件下没有会话记录。</ConsoleEmpty>
                    </TableCell>
                  </TableRow>
                ) : (
                  items.map((session) => (
                    <TableRow
                      key={session.conversationId}
                      className="cursor-pointer"
                      onClick={() => setDetailConversationId(session.conversationId)}
                    >
                      <TableCell className="px-5 py-3">
                        <LongText className="max-w-64 text-sm font-medium">
                          {session.title || "未命名会话"}
                        </LongText>
                        <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                          #{session.conversationId}
                        </p>
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <p className="text-sm">{session.agent || "—"}</p>
                        <p className="text-[10px] text-muted-foreground">
                          {session.agentSource || "—"}
                        </p>
                      </TableCell>
                      <TableCell className="px-5 py-3 text-sm">{session.model || "—"}</TableCell>
                      <TableCell className="px-5 py-3">
                        <Badge variant="outline">{sessionStatusLabel(session.status)}</Badge>
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                        {formatNumber(session.messageCount)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                        {formatNumber(session.requestCount)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                        {formatTokenCount(session.totalTokens)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                        {formatPoints(session.points) ?? "—"}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                        {formatSessionDuration(session.durationSeconds)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                        {formatUserDateTime(session.lastActivityAt)}
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={(event) => {
                            event.stopPropagation();
                            setDetailConversationId(session.conversationId);
                          }}
                        >
                          查看
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
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
          disabled={sessionsQuery.isFetching}
          className="mt-auto"
        />

        {detailConversationId != null ? (
          <SessionDetailDialog
            conversationId={detailConversationId}
            onClose={() => setDetailConversationId(null)}
          />
        ) : null}
      </div>
    </ConsolePage>
  );
}
