import { useMemo, useState } from "react";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { format } from "date-fns";
import {
  Activity,
  AlertTriangle,
  Download,
  Loader2,
  RefreshCw,
  Search,
  Users,
  Waypoints,
} from "lucide-react";
import { toast } from "sonner";

import { DataTableBulkActions, DataTablePagination, LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { downloadCsv } from "@/lib/csv";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  formatAuditExactTime,
  localDateTimeToIso,
  todayAuditTimeRange,
} from "@/modules/workspace/console/audit/lib/api";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";
import { useAllowedOperations, useMenuPath } from "@/modules/workspace/lib/permissions";

import { type JourneyItem, fetchJourneysPage } from "./lib/api";
import { journeysToCsv } from "./lib/journey-csv";

type JourneyFilterForm = {
  from: string;
  to: string;
  uid: string;
};

function defaultFilters(): JourneyFilterForm {
  return {
    ...todayAuditTimeRange(),
    uid: "",
  };
}

export function TracesView() {
  const navigate = useNavigate();
  const tracesPath = useMenuPath("traces");
  const allowedOperations = useAllowedOperations();
  const canExport = allowedOperations.has("export");
  const [draftFilters, setDraftFilters] = useState<JourneyFilterForm>(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState<JourneyFilterForm>(draftFilters);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const query = useQuery({
    queryKey: ["user-journeys", appliedFilters, page, size],
    queryFn: () =>
      fetchJourneysPage({
        from: localDateTimeToIso(appliedFilters.from),
        to: localDateTimeToIso(appliedFilters.to),
        uid: appliedFilters.uid.trim() || undefined,
        page,
        size,
        summary: true,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const journeys = useMemo(
    () =>
      (query.data?.items ?? [])
        .filter((journey): journey is JourneyItem => Boolean(journey.uid))
        .sort((left, right) => (right.end ?? "").localeCompare(left.end ?? "")),
    [query.data],
  );
  const total = query.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);
  const pageViews = query.data?.pageViews ?? 0;
  const actionCount = query.data?.actionCount ?? 0;
  const failedUsers = journeys.filter((journey) => journey.status !== "SUCCESS").length;
  const truncated = Boolean(query.data?.truncated);
  const visibleUids = journeys.map((journey) => journey.uid);
  const allSelected = visibleUids.length > 0 && visibleUids.every((uid) => selected.has(uid));
  const someSelected = visibleUids.some((uid) => selected.has(uid)) && !allSelected;
  const from = localDateTimeToIso(appliedFilters.from);
  const to = localDateTimeToIso(appliedFilters.to);

  const applyFilters = () => {
    if (
      draftFilters.from &&
      draftFilters.to &&
      new Date(draftFilters.from).getTime() >= new Date(draftFilters.to).getTime()
    ) {
      toast.error("结束时间必须晚于开始时间。");
      return;
    }
    setPage(1);
    setSelected(new Set());
    setAppliedFilters({ ...draftFilters });
  };

  const resetFilters = () => {
    const next = defaultFilters();
    setDraftFilters(next);
    setAppliedFilters(next);
    setPage(1);
    setSelected(new Set());
  };

  const toggleAll = () => {
    setSelected((current) => {
      const next = new Set(current);
      if (allSelected) {
        visibleUids.forEach((uid) => next.delete(uid));
      } else {
        visibleUids.forEach((uid) => next.add(uid));
      }
      return next;
    });
  };

  const toggleOne = (uid: string, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(uid);
      } else {
        next.delete(uid);
      }
      return next;
    });
  };

  const exportSelected = () => {
    const items = journeys.filter((journey) => selected.has(journey.uid));
    if (items.length === 0) {
      toast.info("请先选择用户。");
      return;
    }
    downloadCsv(`journeys-${format(new Date(), "yyyyMMdd-HHmm")}.csv`, journeysToCsv(items));
    toast.success(`已导出 ${items.length} 位用户的链路。`);
    setSelected(new Set());
  };

  const journeySearch = {
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
  };

  return (
    <ConsolePage title="链路">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">链路</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              查看用户从哪个页面进入、访问了哪些页面和资源、在页面上做了什么。
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
          >
            {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        <ConsoleSection>
          <ConsoleSectionHeader title="筛选条件" description="默认查看今天的链路" />
          <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
            <div className="space-y-2">
              <Label htmlFor="journey-from">开始时间</Label>
              <Input
                id="journey-from"
                type="datetime-local"
                value={draftFilters.from}
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, from: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="journey-to">结束时间</Label>
              <Input
                id="journey-to"
                type="datetime-local"
                value={draftFilters.to}
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, to: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="journey-uid">用户 UID</Label>
              <Input
                id="journey-uid"
                value={draftFilters.uid}
                placeholder="留空查看全部用户"
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, uid: event.target.value }))
                }
              />
            </div>
            <div className="flex items-end gap-2">
              <Button type="button" onClick={applyFilters} disabled={query.isFetching}>
                {query.isFetching ? <Loader2 className="animate-spin" /> : <Search />}
                查询
              </Button>
              <Button type="button" variant="outline" onClick={resetFilters}>
                重置
              </Button>
            </div>
          </div>
        </ConsoleSection>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <ConsoleStat label="活跃用户" value={total} icon={Users} />
          <ConsoleStat label="页面访问" value={pageViews} icon={Waypoints} />
          <ConsoleStat label="操作总数" value={actionCount} icon={Activity} />
          <ConsoleStat label="本页异常用户" value={failedUsers} icon={AlertTriangle} />
        </div>

        {truncated ? (
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-600 dark:text-amber-400">
            匹配的审计记录超过后端聚合上限，请缩小时间范围或指定用户后再查看完整链路。
          </div>
        ) : null}

        <ConsoleSection>
          <ConsoleSectionHeader
            title="链路列表"
            description="按最近活动倒序；点击用户查看完整链路"
            action={<Badge variant="secondary">共 {total.toLocaleString("zh-CN")} 位用户</Badge>}
          />
          {query.isError ? (
            <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
              <p className="text-sm font-medium">无法加载链路</p>
              <p className="text-xs text-muted-foreground">请检查筛选条件或登录状态后重试。</p>
              <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
                重试
              </Button>
            </div>
          ) : query.isPending ? (
            <div className="space-y-3 p-5">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : journeys.length === 0 ? (
            <ConsoleEmpty>当前条件下没有用户活动记录。</ConsoleEmpty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  {canExport ? (
                    <TableHead className="w-12 px-5">
                      <Checkbox
                        checked={allSelected ? true : someSelected ? "indeterminate" : false}
                        onCheckedChange={toggleAll}
                        aria-label="选择当前页全部用户"
                      />
                    </TableHead>
                  ) : null}
                  <TableHead className="px-5">用户</TableHead>
                  <TableHead className="px-5">活动时间</TableHead>
                  <TableHead className="px-5">入口 → 出口</TableHead>
                  <TableHead className="px-5">页面 / 操作</TableHead>
                  <TableHead className="px-5">状态</TableHead>
                  <TableHead className="px-5 text-end">详情</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {journeys.map((journey) => {
                  const detailPath = tracesPath
                    ? `${tracesPath}/user/${encodeURIComponent(journey.uid)}`
                    : null;
                  return (
                    <TableRow
                      key={journey.uid}
                      className={detailPath ? "cursor-pointer" : undefined}
                      onClick={() => {
                        if (!detailPath) {
                          return;
                        }
                        void navigate({
                          to: "/$",
                          params: { _splat: detailPath },
                          search: journeySearch,
                        });
                      }}
                    >
                      {canExport ? (
                        <TableCell
                          className="px-5 py-3"
                          onClick={(event) => event.stopPropagation()}
                        >
                          <Checkbox
                            checked={selected.has(journey.uid)}
                            onCheckedChange={(checked) => toggleOne(journey.uid, Boolean(checked))}
                            aria-label={`选择用户 ${journey.username || journey.uid}`}
                          />
                        </TableCell>
                      ) : null}
                      <TableCell className="px-5 py-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">
                            {journey.username || journey.uid}
                          </p>
                          <LongText className="max-w-48 font-mono text-xs text-muted-foreground">
                            {journey.uid}
                          </LongText>
                        </div>
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <p className="text-xs text-muted-foreground tabular-nums">
                          {formatAuditExactTime(journey.start)}
                        </p>
                        <p className="text-xs text-muted-foreground tabular-nums">
                          → {formatAuditExactTime(journey.end)}
                        </p>
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <div className="min-w-0">
                          <LongText className="max-w-48 font-mono text-xs text-muted-foreground">
                            {journey.entryPath || "—"}
                          </LongText>
                          <LongText className="max-w-48 font-mono text-[11px] text-muted-foreground">
                            → {journey.exitPath || "—"}
                          </LongText>
                        </div>
                      </TableCell>
                      <TableCell className="px-5 py-3 text-sm text-muted-foreground tabular-nums">
                        <span className="text-foreground">{journey.pageCount ?? 0}</span> 页
                        <span className="mx-1 text-border">/</span>
                        <span className="text-foreground">{journey.actionCount ?? 0}</span> 操作
                      </TableCell>
                      <TableCell className="px-5 py-3">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <AuditResultBadge result={journey.status ?? "SUCCESS"} />
                          <Badge variant={journey.complete ? "outline" : "destructive"}>
                            {journey.complete ? "完整" : "缺少入口"}
                          </Badge>
                        </div>
                      </TableCell>
                      <TableCell className="px-5 py-3 text-end">
                        {detailPath ? (
                          <Button variant="ghost" size="sm" asChild>
                            <Link
                              to="/$"
                              params={{ _splat: detailPath }}
                              search={journeySearch}
                              onClick={(event) => event.stopPropagation()}
                            >
                              查看
                            </Link>
                          </Button>
                        ) : (
                          "—"
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </ConsoleSection>

        <DataTablePagination
          currentPage={currentPage}
          pageCount={pageCount}
          pageSize={size}
          onPageChange={(value) => {
            setPage(value);
            setSelected(new Set());
          }}
          onPageSizeChange={(value) => {
            setSize(value);
            setPage(1);
            setSelected(new Set());
          }}
          disabled={query.isFetching}
          className="mt-auto"
        />

        {canExport ? (
          <DataTableBulkActions
            selectedCount={selected.size}
            entityName="用户"
            onClear={() => setSelected(new Set())}
          >
            <Button type="button" variant="outline" size="sm" onClick={exportSelected}>
              <Download />
              导出选中
            </Button>
          </DataTableBulkActions>
        ) : null}
      </div>
    </ConsolePage>
  );
}
