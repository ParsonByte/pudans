import { useMemo, useState } from "react";

import type { AuditRecord } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { format } from "date-fns";
import {
  Activity,
  CheckCircle2,
  Loader2,
  RefreshCw,
  Search,
  ShieldAlert,
  Users,
} from "lucide-react";

import { ActivityBarChart } from "@/components/activity-bar-chart";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { ConsoleStat } from "@/modules/workspace/console/components/console-stat";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { AuditLogDetailDialog } from "./components/audit-log-detail-dialog";
import { AuditResultBadge } from "./components/audit-result-badge";
import {
  auditVolumeByHour,
  fetchTodayAuditLogs,
  formatAuditExactTime,
  formatAuditHour,
} from "./lib/api";
import { auditOperationLabel, auditResourceLabel } from "./lib/audit-labels";

const EMPTY_RECORDS: AuditRecord[] = [];

export function AuditView() {
  const auditPath = useMenuPath("audit");
  const [detailTarget, setDetailTarget] = useState<AuditRecord | null>(null);
  const todayKey = format(new Date(), "yyyy-MM-dd");
  const auditQuery = useQuery({
    queryKey: ["audit-overview", todayKey],
    queryFn: () => fetchTodayAuditLogs(500),
    retry: false,
    staleTime: 30_000,
  });

  const records = auditQuery.data?.items ?? EMPTY_RECORDS;
  const stats = useMemo(() => {
    const success = records.filter((record) => record.result === "SUCCESS").length;
    const failed = records.filter(
      (record) => record.result === "FAILURE" || record.result === "DENIED",
    ).length;
    const users = new Set(records.map((record) => record.uid).filter(Boolean)).size;
    return { success, failed, users };
  }, [records]);
  const volume = useMemo(() => auditVolumeByHour(records), [records]);
  const recent = records.slice(0, 20);

  return (
    <ConsolePage title="审计">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-2xl font-bold tracking-tight">审计概览</h2>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => void auditQuery.refetch()}
              disabled={auditQuery.isFetching}
            >
              {auditQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            {auditPath ? (
              <Button asChild>
                <Link to="/$" params={{ _splat: `${auditPath}/logs` }}>
                  <Search />
                  查询日志
                </Link>
              </Button>
            ) : null}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <ConsoleStat label="今日事件" value={records.length} icon={Activity} />
          <ConsoleStat label="成功" value={stats.success} icon={CheckCircle2} />
          <ConsoleStat label="失败/拒绝" value={stats.failed} icon={ShieldAlert} />
          <ConsoleStat label="涉及用户" value={stats.users} icon={Users} />
        </div>

        {auditQuery.isError ? (
          <ConsoleSection className="flex min-h-48 flex-col items-center justify-center gap-3 p-6 text-center">
            <ShieldAlert className="size-8 text-muted-foreground" />
            <div className="space-y-1">
              <p className="text-sm font-medium">无法加载审计日志</p>
              <p className="text-xs text-muted-foreground">请检查网络或登录状态后重试。</p>
            </div>
            <Button variant="outline" size="sm" onClick={() => void auditQuery.refetch()}>
              重试
            </Button>
          </ConsoleSection>
        ) : (
          <>
            <ConsoleSection>
              <ConsoleSectionHeader
                title="今日事件量"
                description="按小时统计今日审计事件"
                action={
                  auditQuery.data?.hasMore ? (
                    <span className="text-xs text-muted-foreground">仅统计最近 500 条</span>
                  ) : null
                }
              />
              {auditQuery.isPending ? (
                <div className="p-5">
                  <Skeleton className="h-64 w-full" />
                </div>
              ) : volume.length === 0 ? (
                <ConsoleEmpty>今日暂无审计事件。</ConsoleEmpty>
              ) : (
                <ActivityBarChart
                  points={volume}
                  seriesLabel="事件数"
                  formatLabel={formatAuditHour}
                  height={280}
                />
              )}
            </ConsoleSection>

            <ConsoleSection>
              <ConsoleSectionHeader
                title="最近事件"
                description="审计日志中的最近记录，点击查看详情"
                action={<Badge variant="secondary">{recent.length} 条</Badge>}
              />
              {auditQuery.isPending ? (
                <div className="space-y-3 p-5">
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                  <Skeleton className="h-12 w-full" />
                </div>
              ) : recent.length === 0 ? (
                <ConsoleEmpty>暂无审计记录。</ConsoleEmpty>
              ) : (
                <ul className="divide-y">
                  {recent.map((record) => (
                    <li
                      key={record.metadata.name}
                      className="cursor-pointer px-5 py-3 transition-colors hover:bg-muted/50"
                      onClick={() => setDetailTarget(record)}
                    >
                      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <div className="min-w-0 space-y-1">
                          <p className="truncate text-sm font-medium">
                            {auditOperationLabel(record.operation)}
                            <span className="ms-2 text-xs font-normal text-muted-foreground">
                              {auditResourceLabel(record.resourceType)}
                            </span>
                          </p>
                          <p className="truncate text-xs text-muted-foreground">
                            {record.uid || "匿名"}
                            {record.resourceName ? ` · ${record.resourceName}` : ""}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-3">
                          <AuditResultBadge result={record.result} />
                          <span className="text-xs text-muted-foreground tabular-nums">
                            {formatAuditExactTime(record.timestamp)}
                          </span>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </ConsoleSection>
          </>
        )}
      </div>

      <AuditLogDetailDialog record={detailTarget} onClose={() => setDetailTarget(null)} />
    </ConsolePage>
  );
}
