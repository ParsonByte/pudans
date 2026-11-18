import { useState } from "react";

import type { AuditRecord } from "@pudans-dev/api-client";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeft, Loader2, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { DataTablePagination, LongText } from "@/components/data-table";
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
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { AuditLogDetailDialog } from "./components/audit-log-detail-dialog";
import { AuditResultBadge } from "./components/audit-result-badge";
import {
  type AuditLogFilters,
  defaultAuditTimeRange,
  fetchAuditLogsPage,
  formatAuditExactTime,
  localDateTimeToIso,
} from "./lib/api";
import {
  AUDIT_OPERATION_OPTIONS,
  AUDIT_RESULT_OPTIONS,
  auditOperationLabel,
  auditResourceLabel,
} from "./lib/audit-labels";

const ALL = "ALL";

type AuditFilterForm = {
  from: string;
  to: string;
  operation: string;
  result: string;
  uid: string;
  traceId: string;
  traceFlowId: string;
};

function defaultFilters(): AuditFilterForm {
  const range = defaultAuditTimeRange();
  return {
    ...range,
    operation: ALL,
    result: ALL,
    uid: "",
    traceId: "",
    traceFlowId: "",
  };
}

function toQueryFilters(filters: AuditFilterForm): AuditLogFilters {
  return {
    from: localDateTimeToIso(filters.from),
    to: localDateTimeToIso(filters.to),
    operation: filters.operation === ALL ? undefined : filters.operation,
    result: filters.result === ALL ? undefined : filters.result,
    uid: filters.uid.trim() || undefined,
    traceId: filters.traceId.trim() || undefined,
    traceFlowId: filters.traceFlowId.trim() || undefined,
  };
}

export function AuditLogsView() {
  const auditPath = useMenuPath("audit");
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(draftFilters);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [detailTarget, setDetailTarget] = useState<AuditRecord | null>(null);

  const query = useQuery({
    queryKey: ["audit-logs-page", appliedFilters, page, size],
    queryFn: () =>
      fetchAuditLogsPage({
        ...toQueryFilters(appliedFilters),
        page,
        size,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const records = query.data?.items ?? [];
  const total = query.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);

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
    setAppliedFilters({ ...draftFilters });
  };

  const resetFilters = () => {
    const next = defaultFilters();
    setDraftFilters(next);
    setAppliedFilters(next);
    setPage(1);
  };

  return (
    <ConsolePage title="审计日志">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center gap-2">
          {auditPath ? (
            <Button variant="ghost" size="sm" asChild>
              <Link to="/$" params={{ _splat: auditPath }}>
                <ArrowLeft />
                返回审计
              </Link>
            </Button>
          ) : null}
          <h2 className="text-2xl font-bold tracking-tight">查询日志</h2>
          <Button
            type="button"
            variant="outline"
            className="ms-auto"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
          >
            {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>

        <ConsoleSection>
          <ConsoleSectionHeader
            title="筛选条件"
            description="按时间、操作、结果或链路筛选审计日志"
          />
          <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
            <div className="space-y-2">
              <Label htmlFor="audit-from">开始时间</Label>
              <Input
                id="audit-from"
                type="datetime-local"
                value={draftFilters.from}
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, from: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-to">结束时间</Label>
              <Input
                id="audit-to"
                type="datetime-local"
                value={draftFilters.to}
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, to: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-operation">操作</Label>
              <Select
                value={draftFilters.operation}
                onValueChange={(value) =>
                  setDraftFilters((current) => ({ ...current, operation: value }))
                }
              >
                <SelectTrigger id="audit-operation" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>全部操作</SelectItem>
                  {AUDIT_OPERATION_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-result">结果</Label>
              <Select
                value={draftFilters.result}
                onValueChange={(value) =>
                  setDraftFilters((current) => ({ ...current, result: value }))
                }
              >
                <SelectTrigger id="audit-result" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>全部结果</SelectItem>
                  {AUDIT_RESULT_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-uid">用户 UID</Label>
              <Input
                id="audit-uid"
                value={draftFilters.uid}
                placeholder="精确匹配用户 UID"
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, uid: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-trace">Trace ID</Label>
              <Input
                id="audit-trace"
                value={draftFilters.traceId}
                placeholder="精确匹配 Trace ID"
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, traceId: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="audit-trace-flow">Trace Flow ID</Label>
              <Input
                id="audit-trace-flow"
                value={draftFilters.traceFlowId}
                placeholder="精确匹配 Trace Flow ID"
                onChange={(event) =>
                  setDraftFilters((current) => ({ ...current, traceFlowId: event.target.value }))
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

        <ConsoleSection>
          <ConsoleSectionHeader
            title="日志列表"
            description="点击任意日志查看完整日志、上下文和操作快照"
            action={<Badge variant="secondary">共 {total.toLocaleString("zh-CN")} 条</Badge>}
          />
          {query.isError ? (
            <div className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
              <p className="text-sm font-medium">无法加载审计日志</p>
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
              <Skeleton className="h-10 w-full" />
            </div>
          ) : records.length === 0 ? (
            <ConsoleEmpty>没有匹配的审计日志。</ConsoleEmpty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">时间</TableHead>
                  <TableHead className="px-5">操作</TableHead>
                  <TableHead className="px-5">资源类型</TableHead>
                  <TableHead className="px-5">资源名称</TableHead>
                  <TableHead className="px-5">结果</TableHead>
                  <TableHead className="px-5">用户</TableHead>
                  <TableHead className="px-5">请求</TableHead>
                  <TableHead className="px-5">Trace Flow ID</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.map((record) => (
                  <TableRow
                    key={record.metadata.name}
                    className="cursor-pointer"
                    onClick={() => setDetailTarget(record)}
                  >
                    <TableCell className="px-5 py-2.5 text-xs text-muted-foreground tabular-nums">
                      {formatAuditExactTime(record.timestamp)}
                    </TableCell>
                    <TableCell className="px-5 py-2.5 text-sm">
                      {auditOperationLabel(record.operation)}
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <Badge variant="outline">{auditResourceLabel(record.resourceType)}</Badge>
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <LongText className="max-w-48 font-mono text-xs text-muted-foreground">
                        {record.resourceName || "—"}
                      </LongText>
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <AuditResultBadge result={record.result} />
                    </TableCell>
                    <TableCell className="px-5 py-2.5 font-mono text-xs text-muted-foreground">
                      {record.uid || "—"}
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <div className="min-w-0">
                        <p className="text-xs text-muted-foreground">
                          {record.resourceType === "Page" ? "PAGE" : record.requestMethod || "—"}
                        </p>
                        <LongText className="max-w-64 font-mono text-xs text-muted-foreground">
                          {record.resourceType === "Page"
                            ? record.resourceName || record.requestPath || "—"
                            : record.requestPath || "—"}
                        </LongText>
                      </div>
                    </TableCell>
                    <TableCell className="px-5 py-2.5">
                      <LongText className="max-w-40 font-mono text-xs text-muted-foreground">
                        {record.traceFlowId || "—"}
                      </LongText>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </ConsoleSection>

        <DataTablePagination
          currentPage={currentPage}
          pageCount={pageCount}
          pageSize={size}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
          disabled={query.isFetching}
          className="mt-auto"
        />
      </div>

      <AuditLogDetailDialog record={detailTarget} onClose={() => setDetailTarget(null)} />
    </ConsolePage>
  );
}
