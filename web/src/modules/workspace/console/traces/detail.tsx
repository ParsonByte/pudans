import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  RefreshCw,
  Waypoints,
} from "lucide-react";

import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import { auditResourceLabel } from "@/modules/workspace/console/audit/lib/audit-labels";
import {
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { TraceSpanDetailDialog } from "./components/trace-span-detail-dialog";
import { TraceTree } from "./components/trace-tree";
import { fetchTraceFlowsPage } from "./lib/api";
import { type TraceSpanNode, buildTraceTree, formatDuration, formatTraceTime } from "./lib/traces";

type TraceDetailViewProps = {
  traceFlowId: string;
};

type SpanSelection = {
  span: TraceSpanNode;
  parent?: TraceSpanNode;
};

export function TraceDetailView({ traceFlowId }: TraceDetailViewProps) {
  const tracesPath = useMenuPath("traces");
  const [selection, setSelection] = useState<SpanSelection | null>(null);
  const query = useQuery({
    queryKey: ["trace-flow", traceFlowId],
    queryFn: () => fetchTraceFlowsPage({ traceFlowId, page: 1, size: 1 }),
    retry: false,
  });

  const flow = query.data?.items?.[0];
  const records = useMemo(() => flow?.records ?? [], [flow]);
  const tree = useMemo(() => buildTraceTree(records), [records]);

  if (query.isPending) {
    return (
      <ConsolePage title="链路详情">
        <div className="space-y-4">
          <Skeleton className="h-10 w-40" />
          <Skeleton className="h-40 rounded-xl" />
          <Skeleton className="h-80 rounded-xl" />
        </div>
      </ConsolePage>
    );
  }

  if (query.isError || !flow) {
    return (
      <ConsolePage title="链路详情">
        <ConsoleSection className="flex min-h-64 flex-col items-center justify-center gap-4 p-8 text-center">
          <Waypoints className="size-8 text-muted-foreground" />
          <div className="space-y-1">
            <h2 className="text-sm font-semibold">无法加载链路</h2>
            <p className="text-xs text-muted-foreground">该链路不存在或没有审计记录。</p>
          </div>
          <div className="flex gap-2">
            {tracesPath ? (
              <Button variant="outline" size="sm" asChild>
                <Link to="/$" params={{ _splat: tracesPath }}>
                  <ArrowLeft />
                  返回链路列表
                </Link>
              </Button>
            ) : null}
            <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
              <RefreshCw />
              重试
            </Button>
          </div>
        </ConsoleSection>
      </ConsolePage>
    );
  }

  const traceIds = flow.traceIds ?? tree.traceIds;
  const resourceLabels = (flow.resourceTypes ?? []).map(auditResourceLabel);
  const status = flow.status ?? "SUCCESS";

  return (
    <ConsolePage title="链路详情">
      <div className="flex flex-wrap items-center gap-2">
        {tracesPath ? (
          <Button variant="ghost" size="sm" asChild>
            <Link to="/$" params={{ _splat: tracesPath }}>
              <ArrowLeft />
              返回链路列表
            </Link>
          </Button>
        ) : null}
        <div className="ms-auto">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void query.refetch()}
            disabled={query.isFetching}
          >
            {query.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
        </div>
      </div>

      <ConsoleSection>
        <ConsoleSectionHeader
          title="链路概览"
          description="从入口 Span 到全部子 Span 的调用摘要"
          action={
            tree.completeness.complete ? (
              <Badge variant="secondary">
                <CheckCircle2 />
                链路闭环
              </Badge>
            ) : (
              <Badge variant="destructive">
                <AlertTriangle />
                链路不完整
              </Badge>
            )
          }
        />
        <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
          <InfoRow
            label="Trace Flow ID"
            value={<span className="font-mono text-xs break-all">{traceFlowId}</span>}
            className="sm:col-span-2"
          />
          <InfoRow
            label="Trace ID"
            value={
              <span className="font-mono text-xs break-all">{traceIds.join("、") || "—"}</span>
            }
            className="sm:col-span-2"
          />
          <InfoRow label="开始时间" value={formatTraceTime(tree.startMs)} />
          <InfoRow label="结束时间" value={formatTraceTime(tree.endMs)} />
          <InfoRow label="总耗时" value={formatDuration(flow.durationMs ?? tree.totalMs)} />
          <InfoRow label="链路状态" value={<AuditResultBadge result={status} />} />
          <InfoRow
            label="Span 总数"
            value={`${flow.spanCount ?? tree.spanCount}（入口 ${tree.completeness.entrySpanCount} · 孤立 ${tree.completeness.orphanSpanCount}）`}
          />
          <InfoRow label="用户 UID" value={flow.uid || "—"} />
          <InfoRow
            label="涉及资源"
            value={resourceLabels.length > 0 ? resourceLabels.join("、") : "—"}
            className="sm:col-span-2"
          />
          <InfoRow
            label="Trace 数量"
            value={`${traceIds.length} 个 Trace ID`}
            className="sm:col-span-2"
          />
        </div>
        <div
          className={cn(
            "mx-5 mb-5 flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2 text-xs",
            tree.completeness.complete
              ? "bg-muted/40 text-muted-foreground"
              : "border-destructive/30 bg-destructive/5 text-destructive",
          )}
        >
          {tree.completeness.complete ? (
            <>
              <CheckCircle2 className="size-3.5" />
              闭环检查通过：{tree.completeness.entrySpanCount} 个入口 Span，
              {tree.spanCount} 个 Span 全部接入链路。
            </>
          ) : (
            <>
              <AlertTriangle className="size-3.5" />
              闭环检查未通过：{tree.completeness.issues.join("；")}。
            </>
          )}
        </div>
      </ConsoleSection>

      <ConsoleSection>
        <ConsoleSectionHeader
          title="调用时间线"
          description="按父子关系展开或收起 Span，点击任意 Span 查看详细信息"
        />
        <TraceTree tree={tree} onSelect={(span, parent) => setSelection({ span, parent })} />
      </ConsoleSection>

      <TraceSpanDetailDialog
        span={selection?.span ?? null}
        parentSpan={selection?.parent}
        traceFlowId={traceFlowId}
        traceTotalMs={tree.totalMs}
        onClose={() => setSelection(null)}
      />
    </ConsolePage>
  );
}
