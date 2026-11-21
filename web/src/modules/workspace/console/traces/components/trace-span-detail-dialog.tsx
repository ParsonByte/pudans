import type { AuditTrace, TraceStep } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import { fetchAuditTrace, formatAuditExactTime } from "@/modules/workspace/console/audit/lib/api";
import { formatAuditValue } from "@/modules/workspace/console/audit/lib/audit-format";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

import {
  SPAN_KIND_BADGE_VARIANTS,
  type TraceSpanNode,
  formatDuration,
  formatTraceTime,
  spanKindLabel,
} from "../lib/traces";

type TraceSpanDetailDialogProps = {
  span: TraceSpanNode | null;
  parentSpan?: TraceSpanNode;
  traceFlowId: string;
  traceTotalMs: number;
  onClose: () => void;
};

export function TraceSpanDetailDialog({
  span,
  parentSpan,
  traceFlowId,
  traceTotalMs,
  onClose,
}: TraceSpanDetailDialogProps) {
  const record = span?.record;
  const traceQuery = useQuery({
    queryKey: ["audit-trace", record?.metadata.name],
    queryFn: () => fetchAuditTrace(record?.metadata.name ?? ""),
    enabled: Boolean(record),
    retry: false,
  });
  const contextEntries = Object.entries((record?.context ?? {}) as Record<string, unknown>);
  const durationRatio =
    span && traceTotalMs > 0 ? Math.min(100, (span.durationMs / traceTotalMs) * 100) : 0;
  const barColor =
    record?.result !== "SUCCESS"
      ? "bg-destructive"
      : span?.kind === "ORPHAN"
        ? "bg-warning"
        : "bg-primary";

  return (
    <DetailDialog
      open={Boolean(span)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={
        <span className="flex flex-wrap items-center gap-2">
          {record ? auditOperationLabel(record.operation) : "Span 详情"}
          {span ? (
            <Badge variant={SPAN_KIND_BADGE_VARIANTS[span.kind]}>{spanKindLabel(span.kind)}</Badge>
          ) : null}
          {record ? <AuditResultBadge result={record.result} /> : null}
        </span>
      }
      description={
        record && span
          ? `${auditResourceLabel(record.resourceType)}${
              record.resourceName ? ` · ${record.resourceName}` : ""
            } · ${formatTraceTime(span.startMs)}`
          : ""
      }
      size="3xl"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      {span && record ? (
        <div className="space-y-5">
          <section className="space-y-3 border p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">Span 时间轴</h3>
              <span className="text-xs text-muted-foreground">
                入口偏移 {formatDuration(span.offsetMs)} · 占总耗时 {durationRatio.toFixed(1)}%
              </span>
            </div>
            <div className="relative h-3 overflow-hidden bg-muted">
              <div
                className={cn("absolute inset-y-0", barColor)}
                style={{
                  left: `${span.relativeOffset}%`,
                  width: `${span.relativeWidth}%`,
                }}
              />
            </div>
            <div className="flex flex-wrap justify-between gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
              <span>开始 {formatTraceTime(span.startMs)}</span>
              <span>结束 {formatTraceTime(span.endMs)}</span>
              <span>耗时 {formatDuration(span.durationMs)}</span>
              <span>链路总耗时 {formatDuration(traceTotalMs)}</span>
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold">Span 标识</h3>
            <div className="grid gap-4 border p-3 sm:grid-cols-2">
              <InfoRow
                label="Span ID"
                value={<span className="font-mono text-xs break-all">{record.metadata.name}</span>}
              />
              <InfoRow
                label="父 Span ID"
                value={
                  <span className="font-mono text-xs break-all">{record.parentAuditId || "—"}</span>
                }
              />
              <InfoRow
                label="Trace ID"
                value={<span className="font-mono text-xs break-all">{record.traceId || "—"}</span>}
              />
              <InfoRow
                label="Trace Flow ID"
                value={<span className="font-mono text-xs break-all">{traceFlowId}</span>}
              />
              <InfoRow label="步骤序号" value={record.stepIndex ?? "—"} />
              <InfoRow label="归档状态" value={record.archived ? "已归档" : "活跃"} />
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold">链路位置</h3>
            <div className="border p-3">
              {traceQuery.isPending ? (
                <p className="text-xs text-muted-foreground">正在加载链路前后的步骤。</p>
              ) : traceQuery.isError ? (
                <p className="text-xs text-muted-foreground">无法加载该 Span 的链路前后步骤。</p>
              ) : traceQuery.data ? (
                <TracePosition trace={traceQuery.data} />
              ) : null}
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold">调用关系</h3>
            <div className="grid gap-4 border p-3 sm:grid-cols-2">
              <InfoRow
                label="Span 类型"
                value={
                  <Badge variant={SPAN_KIND_BADGE_VARIANTS[span.kind]}>
                    {spanKindLabel(span.kind)}
                  </Badge>
                }
              />
              <InfoRow label="子 Span 数" value={span.children.length} />
              <InfoRow
                label="服务"
                value={<Badge variant="outline">{auditResourceLabel(record.resourceType)}</Badge>}
              />
              <InfoRow
                label="资源名称"
                value={
                  <span className="font-mono text-xs break-all">{record.resourceName || "—"}</span>
                }
              />
              <InfoRow
                label="父 Span"
                value={
                  parentSpan
                    ? `${auditOperationLabel(parentSpan.record.operation)} · ${parentSpan.record.metadata.name}`
                    : "—"
                }
                className="sm:col-span-2"
              />
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold">执行信息</h3>
            <div className="grid gap-4 border p-3 sm:grid-cols-2">
              <InfoRow label="用户 UID" value={record.uid || "—"} />
              <InfoRow label="结果" value={<AuditResultBadge result={record.result} />} />
              <InfoRow label="请求方法" value={record.requestMethod || "—"} />
              <InfoRow label="客户端 IP" value={record.ip || "—"} />
              <InfoRow
                label="请求路径"
                value={
                  <span className="font-mono text-xs break-all">{record.requestPath || "—"}</span>
                }
                className="sm:col-span-2"
              />
              <InfoRow
                label="User-Agent"
                value={<span className="text-xs break-all">{record.userAgent || "—"}</span>}
                className="sm:col-span-2"
              />
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-sm font-semibold">Span 标签</h3>
            {contextEntries.length === 0 ? (
              <ConsoleState
                variant="empty"
                title="该 Span 没有标签。"
                className="min-h-0 border p-3"
              />
            ) : (
              <div className="grid gap-4 border p-3 sm:grid-cols-2">
                {contextEntries.map(([key, value]) => (
                  <InfoRow
                    key={key}
                    label={key}
                    value={
                      <span className="font-mono text-xs break-all whitespace-pre-wrap">
                        {formatAuditValue(value)}
                      </span>
                    }
                  />
                ))}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </DetailDialog>
  );
}

function TracePosition({ trace }: { trace: AuditTrace }) {
  const before = trace.before ?? [];
  const after = trace.after ?? [];
  const steps = trace.steps ?? [];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <Badge variant="outline">前序 {before.length} 步</Badge>
        <Badge variant="secondary">当前 Span</Badge>
        <Badge variant="outline">后续 {after.length} 步</Badge>
        <span className="text-muted-foreground">链路共 {steps.length} 步</span>
      </div>
      {before.length === 0 && after.length === 0 ? (
        <p className="text-xs text-muted-foreground">该 Span 是链路中的唯一步骤。</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          <TraceNeighbor label="上一步" step={before[before.length - 1]} emptyLabel="链路起点" />
          <TraceNeighbor label="下一步" step={after[0]} emptyLabel="链路终点" />
        </div>
      )}
    </div>
  );
}

type TraceNeighborProps = {
  label: string;
  step?: TraceStep;
  emptyLabel: string;
};

function TraceNeighbor({ label, step, emptyLabel }: TraceNeighborProps) {
  if (!step) {
    return (
      <div className="border bg-muted/20 px-3 py-2 text-xs text-muted-foreground">
        {label}：{emptyLabel}
      </div>
    );
  }
  return (
    <div className="border bg-muted/20 px-3 py-2">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className="mt-1 flex flex-wrap items-center gap-2 text-xs">
        <span className="font-medium">{auditOperationLabel(step.operation)}</span>
        <Badge variant="outline">{auditResourceLabel(step.resourceType)}</Badge>
        <AuditResultBadge result={step.result} />
      </p>
      <p className="mt-1 truncate font-mono text-[11px] text-muted-foreground">
        {step.resourceName || step.auditId || "—"}
      </p>
      <p className="text-[11px] text-muted-foreground tabular-nums">
        {formatAuditExactTime(step.timestamp)}
      </p>
    </div>
  );
}
