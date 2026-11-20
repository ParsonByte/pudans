import { useMemo, useState } from "react";

import type { AuditRecord } from "@pudans-dev/api-client";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { subDays } from "date-fns";
import { Archive, History, Loader2, Play, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { DataTablePagination, LongText } from "@/components/data-table";
import { DateTimePicker } from "@/components/date-time-picker";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { resolveApiError } from "@/lib/api-error";
import { beijingDateTimeInputValue } from "@/lib/date";
import { AuditLogDetailDialog } from "@/modules/workspace/console/audit/components/audit-log-detail-dialog";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  defaultAuditTimeRange,
  formatAuditExactTime,
  localDateTimeToIso,
} from "@/modules/workspace/console/audit/lib/api";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { ReplayPlayer } from "./components/replay-player";
import {
  type ArchivedLogFilters,
  archiveAuditLogs,
  fetchArchiveStats,
  fetchArchivedLogsPage,
  fetchReplayFlows,
} from "./lib/api";

type QueryFilterForm = {
  from: string;
  to: string;
  uid: string;
  traceFlowId: string;
};

type ArchiveFilterForm = {
  from: string;
  to: string;
  uid: string;
};

function defaultQueryFilters(): QueryFilterForm {
  return {
    ...defaultAuditTimeRange(),
    uid: "",
    traceFlowId: "",
  };
}

function defaultArchiveFilters(): ArchiveFilterForm {
  return { from: "", to: "", uid: "" };
}

function defaultCutoff(): string {
  return beijingDateTimeInputValue(subDays(new Date(), 90));
}

export function ReplayView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canArchive = allowedOperations.has("archive");
  const [activeTab, setActiveTab] = useState("query");
  const [queryDraft, setQueryDraft] = useState<QueryFilterForm>(defaultQueryFilters);
  const [queryFilters, setQueryFilters] = useState<QueryFilterForm>(queryDraft);
  const [flowPage, setFlowPage] = useState(1);
  const [flowSize, setFlowSize] = useState(10);
  const [selectedFlowId, setSelectedFlowId] = useState<string | null>(null);
  const [archiveDraft, setArchiveDraft] = useState<ArchiveFilterForm>(defaultArchiveFilters);
  const [archiveFilters, setArchiveFilters] = useState<ArchiveFilterForm>(archiveDraft);
  const [archivePage, setArchivePage] = useState(1);
  const [archiveSize, setArchiveSize] = useState(10);
  const [detailTarget, setDetailTarget] = useState<AuditRecord | null>(null);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [cutoff, setCutoff] = useState(defaultCutoff);

  const flowQuery = useQuery({
    queryKey: ["replay-flows", queryFilters, flowPage, flowSize],
    queryFn: () =>
      fetchReplayFlows({
        from: localDateTimeToIso(queryFilters.from),
        to: localDateTimeToIso(queryFilters.to),
        uid: queryFilters.uid.trim() || undefined,
        traceFlowId: queryFilters.traceFlowId.trim() || undefined,
        page: flowPage,
        size: flowSize,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const flows = useMemo(
    () => (flowQuery.data?.items ?? []).filter((flow) => Boolean(flow.traceFlowId)),
    [flowQuery.data],
  );
  const flowTotal = flowQuery.data?.total ?? 0;
  const flowPageCount = Math.max(1, Math.ceil(flowTotal / flowSize));
  const currentFlowPage = Math.min(flowPage, flowPageCount);

  const statsQuery = useQuery({
    queryKey: ["archive-stats"],
    queryFn: fetchArchiveStats,
    retry: false,
  });
  const stats = statsQuery.data;
  const archiveQuery = useQuery({
    queryKey: ["archived-logs", archiveFilters, archivePage, archiveSize],
    queryFn: () =>
      fetchArchivedLogsPage({
        ...toArchiveFilters(archiveFilters),
        page: archivePage,
        size: archiveSize,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const archivedLogs = archiveQuery.data?.items ?? [];
  const archiveTotal = archiveQuery.data?.total ?? 0;
  const archivePageCount = Math.max(1, Math.ceil(archiveTotal / archiveSize));
  const currentArchivePage = Math.min(archivePage, archivePageCount);

  const archiveMutation = useMutation({
    mutationFn: async () => {
      const before = localDateTimeToIso(cutoff);
      if (!before) {
        throw new Error("请选择归档截止时间。");
      }
      return archiveAuditLogs(before);
    },
    onSuccess: async (result) => {
      toast.success(`已归档 ${result.archivedCount ?? 0} 条审计记录。`);
      setArchiveOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["archive-stats"] }),
        queryClient.invalidateQueries({ queryKey: ["archived-logs"] }),
        queryClient.invalidateQueries({ queryKey: ["replay-flows"] }),
      ]);
    },
    onError: (error) =>
      toast.error(
        resolveApiError(error, {
          notFound: "未找到可归档的审计记录。",
          conflict: "归档任务正在执行，请稍后重试。",
        }),
      ),
  });

  const applyQueryFilters = () => {
    setFlowPage(1);
    setSelectedFlowId(null);
    setQueryFilters({ ...queryDraft });
  };

  const resetQueryFilters = () => {
    const next = defaultQueryFilters();
    setQueryDraft(next);
    setQueryFilters(next);
    setFlowPage(1);
    setSelectedFlowId(null);
  };

  const applyArchiveFilters = () => {
    setArchivePage(1);
    setArchiveFilters({ ...archiveDraft });
  };

  const resetArchiveFilters = () => {
    const next = defaultArchiveFilters();
    setArchiveDraft(next);
    setArchiveFilters(next);
    setArchivePage(1);
  };

  const replayFlow = (traceFlowId?: string) => {
    if (!traceFlowId) {
      return;
    }
    setSelectedFlowId(traceFlowId);
    setActiveTab("replay");
  };

  return (
    <ConsolePage title="回溯">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              void queryClient.invalidateQueries({ queryKey: ["replay-flows"] });
              void queryClient.invalidateQueries({ queryKey: ["archive-stats"] });
              void queryClient.invalidateQueries({ queryKey: ["archived-logs"] });
            }}
            disabled={flowQuery.isFetching || archiveQuery.isFetching}
          >
            {flowQuery.isFetching || archiveQuery.isFetching ? (
              <Loader2 className="animate-spin" />
            ) : (
              <RefreshCw />
            )}
            刷新
          </Button>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="flex flex-1 flex-col gap-4">
          <TabsList>
            <TabsTrigger value="query">回溯查询</TabsTrigger>
            <TabsTrigger value="replay">回放</TabsTrigger>
            <TabsTrigger value="archive">归档管理</TabsTrigger>
          </TabsList>

          <TabsContent value="query" className="flex flex-1 flex-col gap-4">
            <ConsoleSection>
              <ConsoleSectionHeader
                title="查询条件"
                description="按时间、用户或链路查询可回放记录"
              />
              <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
                <div className="space-y-2">
                  <Label htmlFor="replay-from">开始时间</Label>
                  <DateTimePicker
                    id="replay-from"
                    value={queryDraft.from}
                    onChange={(value) => setQueryDraft((current) => ({ ...current, from: value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="replay-to">结束时间</Label>
                  <DateTimePicker
                    id="replay-to"
                    value={queryDraft.to}
                    onChange={(value) => setQueryDraft((current) => ({ ...current, to: value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="replay-uid">用户 UID</Label>
                  <Input
                    id="replay-uid"
                    value={queryDraft.uid}
                    placeholder="留空查询全部用户"
                    onChange={(event) =>
                      setQueryDraft((current) => ({ ...current, uid: event.target.value }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="replay-flow-id">Trace Flow ID</Label>
                  <Input
                    id="replay-flow-id"
                    value={queryDraft.traceFlowId}
                    placeholder="精确匹配 Trace Flow ID"
                    onChange={(event) =>
                      setQueryDraft((current) => ({
                        ...current,
                        traceFlowId: event.target.value,
                      }))
                    }
                  />
                </div>
                <div className="flex items-end gap-2 sm:col-span-2 xl:col-span-4">
                  <Button type="button" onClick={applyQueryFilters} disabled={flowQuery.isFetching}>
                    {flowQuery.isFetching ? <Loader2 className="animate-spin" /> : <Search />}
                    查询
                  </Button>
                  <Button type="button" variant="outline" onClick={resetQueryFilters}>
                    重置
                  </Button>
                </div>
              </div>
            </ConsoleSection>

            <ConsoleSection>
              <ConsoleSectionHeader
                title="可回溯链路"
                description="选择一条链路查看操作回放"
                action={
                  <Badge variant="secondary">共 {flowTotal.toLocaleString("zh-CN")} 条</Badge>
                }
              />
              {flowQuery.isError ? (
                <ConsoleEmpty>无法加载回溯链路，请检查筛选条件后重试。</ConsoleEmpty>
              ) : flowQuery.isPending ? (
                <div className="space-y-3 p-5">
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-10 w-full" />
                </div>
              ) : flows.length === 0 ? (
                <ConsoleEmpty>当前条件下没有可回溯链路。</ConsoleEmpty>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="px-5">开始时间</TableHead>
                      <TableHead className="px-5">用户</TableHead>
                      <TableHead className="px-5">资源</TableHead>
                      <TableHead className="px-5">步骤</TableHead>
                      <TableHead className="px-5">状态</TableHead>
                      <TableHead className="px-5">归档</TableHead>
                      <TableHead className="px-5 text-end">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {flows.map((flow) => (
                      <TableRow
                        key={flow.traceFlowId}
                        className="cursor-pointer"
                        onClick={() => replayFlow(flow.traceFlowId)}
                      >
                        <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                          {formatAuditExactTime(flow.start)}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-40 font-mono text-xs text-muted-foreground">
                            {flow.uid || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-48 text-xs text-muted-foreground">
                            {(flow.resourceTypes ?? []).map(auditResourceLabel).join("、") || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3 text-sm text-muted-foreground tabular-nums">
                          {flow.spanCount ?? 0}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <AuditResultBadge result={flow.status} />
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          {flow.archived ? (
                            <Badge variant="outline">已归档</Badge>
                          ) : (
                            <span className="text-xs text-muted-foreground">活跃</span>
                          )}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={(event) => {
                              event.stopPropagation();
                              replayFlow(flow.traceFlowId);
                            }}
                          >
                            <Play />
                            回放
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </ConsoleSection>

            <DataTablePagination
              currentPage={currentFlowPage}
              pageCount={flowPageCount}
              pageSize={flowSize}
              onPageChange={(value) => {
                setFlowPage(value);
                setSelectedFlowId(null);
              }}
              onPageSizeChange={(value) => {
                setFlowSize(value);
                setFlowPage(1);
                setSelectedFlowId(null);
              }}
              disabled={flowQuery.isFetching}
              className="mt-auto"
            />
          </TabsContent>

          <TabsContent value="replay" className="flex flex-1 flex-col gap-4">
            <ConsoleSection>
              {selectedFlowId ? (
                <ReplayPlayer key={selectedFlowId} traceFlowId={selectedFlowId} />
              ) : (
                <ConsoleEmpty>请先在「回溯查询」中选择一条链路。</ConsoleEmpty>
              )}
            </ConsoleSection>
          </TabsContent>

          <TabsContent value="archive" className="flex flex-1 flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-3">
              <ConsoleStat label="活跃记录" value={stats?.activeCount ?? 0} icon={History} />
              <ConsoleStat label="已归档记录" value={stats?.archivedCount ?? 0} icon={Archive} />
              <ConsoleStat label="总记录" value={stats?.totalCount ?? 0} icon={Archive} />
            </div>

            <ConsoleSection>
              <ConsoleSectionHeader
                title="归档内容"
                description="查看已归档的审计记录，可继续回放或打开详情"
                action={
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="secondary">共 {archiveTotal.toLocaleString("zh-CN")} 条</Badge>
                    {canArchive ? (
                      <Button type="button" size="sm" onClick={() => setArchiveOpen(true)}>
                        <Archive />
                        发起归档
                      </Button>
                    ) : null}
                  </div>
                }
              />
              <div className="grid gap-4 border-b p-5 sm:grid-cols-2 xl:grid-cols-4">
                <div className="space-y-2">
                  <Label htmlFor="archive-from">开始时间</Label>
                  <DateTimePicker
                    id="archive-from"
                    value={archiveDraft.from}
                    onChange={(value) =>
                      setArchiveDraft((current) => ({ ...current, from: value }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="archive-to">结束时间</Label>
                  <DateTimePicker
                    id="archive-to"
                    value={archiveDraft.to}
                    onChange={(value) => setArchiveDraft((current) => ({ ...current, to: value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="archive-uid">用户 UID</Label>
                  <Input
                    id="archive-uid"
                    value={archiveDraft.uid}
                    placeholder="留空查看全部"
                    onChange={(event) =>
                      setArchiveDraft((current) => ({ ...current, uid: event.target.value }))
                    }
                  />
                </div>
                <div className="flex items-end gap-2">
                  <Button
                    type="button"
                    onClick={applyArchiveFilters}
                    disabled={archiveQuery.isFetching}
                  >
                    {archiveQuery.isFetching ? <Loader2 className="animate-spin" /> : <Search />}
                    查询
                  </Button>
                  <Button type="button" variant="outline" onClick={resetArchiveFilters}>
                    重置
                  </Button>
                </div>
              </div>
              {archiveQuery.isError ? (
                <ConsoleEmpty>无法加载归档内容，请稍后重试。</ConsoleEmpty>
              ) : archiveQuery.isPending ? (
                <div className="space-y-3 p-5">
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-10 w-full" />
                  <Skeleton className="h-10 w-full" />
                </div>
              ) : archivedLogs.length === 0 ? (
                <ConsoleEmpty>当前没有匹配的归档记录。</ConsoleEmpty>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="px-5">时间</TableHead>
                      <TableHead className="px-5">操作</TableHead>
                      <TableHead className="px-5">资源</TableHead>
                      <TableHead className="px-5">结果</TableHead>
                      <TableHead className="px-5">用户</TableHead>
                      <TableHead className="px-5">Trace Flow ID</TableHead>
                      <TableHead className="px-5 text-end">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {archivedLogs.map((record) => (
                      <TableRow
                        key={record.metadata.name}
                        className="cursor-pointer"
                        onClick={() => setDetailTarget(record)}
                      >
                        <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                          {formatAuditExactTime(record.timestamp)}
                        </TableCell>
                        <TableCell className="px-5 py-3 text-sm">
                          {auditOperationLabel(record.operation)}
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-48 text-xs text-muted-foreground">
                            {auditResourceLabel(record.resourceType)}
                            {record.resourceName ? ` · ${record.resourceName}` : ""}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <AuditResultBadge result={record.result} />
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-40 font-mono text-xs text-muted-foreground">
                            {record.uid || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3">
                          <LongText className="max-w-40 font-mono text-xs text-muted-foreground">
                            {record.traceFlowId || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-5 py-3 text-end">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={(event) => {
                              event.stopPropagation();
                              replayFlow(record.traceFlowId);
                            }}
                            disabled={!record.traceFlowId}
                          >
                            <Play />
                            回放
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </ConsoleSection>

            <DataTablePagination
              currentPage={currentArchivePage}
              pageCount={archivePageCount}
              pageSize={archiveSize}
              onPageChange={setArchivePage}
              onPageSizeChange={(value) => {
                setArchiveSize(value);
                setArchivePage(1);
              }}
              disabled={archiveQuery.isFetching}
              className="mt-auto"
            />
          </TabsContent>
        </Tabs>
      </div>

      <Dialog open={archiveOpen} onOpenChange={setArchiveOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>发起归档</DialogTitle>
            <DialogDescription>
              将截止时间之前的审计记录移入归档表，归档后可继续查询和回放。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="archive-cutoff">归档截止时间</Label>
            <DateTimePicker id="archive-cutoff" value={cutoff} onChange={setCutoff} />
            <p className="text-xs text-muted-foreground">早于该时间的活跃审计记录会被移入归档。</p>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setArchiveOpen(false)}
              disabled={archiveMutation.isPending}
            >
              取消
            </Button>
            <Button
              type="button"
              onClick={() => archiveMutation.mutate()}
              disabled={archiveMutation.isPending || !cutoff}
            >
              {archiveMutation.isPending ? <Loader2 className="animate-spin" /> : <Archive />}
              确认归档
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AuditLogDetailDialog record={detailTarget} onClose={() => setDetailTarget(null)} />
    </ConsolePage>
  );
}

function toArchiveFilters(filters: ArchiveFilterForm): Omit<ArchivedLogFilters, "page" | "size"> {
  return {
    from: localDateTimeToIso(filters.from),
    to: localDateTimeToIso(filters.to),
    uid: filters.uid.trim() || undefined,
  };
}
