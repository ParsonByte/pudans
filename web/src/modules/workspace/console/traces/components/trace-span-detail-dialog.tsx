import { InfoRow } from "@/components/info-row";
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
import { cn } from "@/lib/utils";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import { formatAuditValue } from "@/modules/workspace/console/audit/lib/audit-format";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";

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
    <Dialog
      open={Boolean(span)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="flex max-h-[85vh] flex-col overflow-hidden sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="flex flex-wrap items-center gap-2">
            {record ? auditOperationLabel(record.operation) : "Span 详情"}
            {span ? (
              <Badge variant={SPAN_KIND_BADGE_VARIANTS[span.kind]}>
                {spanKindLabel(span.kind)}
              </Badge>
            ) : null}
            {record ? <AuditResultBadge result={record.result} /> : null}
          </DialogTitle>
          <DialogDescription>
            {record && span
              ? `${auditResourceLabel(record.resourceType)}${
                  record.resourceName ? ` · ${record.resourceName}` : ""
                } · ${formatTraceTime(span.startMs)}`
              : ""}
          </DialogDescription>
        </DialogHeader>

        {span && record ? (
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto py-4">
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
                  value={
                    <span className="font-mono text-xs break-all">{record.metadata.name}</span>
                  }
                />
                <InfoRow
                  label="父 Span ID"
                  value={
                    <span className="font-mono text-xs break-all">
                      {record.parentAuditId || "—"}
                    </span>
                  }
                />
                <InfoRow
                  label="Trace ID"
                  value={
                    <span className="font-mono text-xs break-all">{record.traceId || "—"}</span>
                  }
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
                    <span className="font-mono text-xs break-all">
                      {record.resourceName || "—"}
                    </span>
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
                <p className="border p-3 text-xs text-muted-foreground">该 Span 没有标签。</p>
              ) : (
                <div className="space-y-2 border p-3">
                  {contextEntries.map(([key, value]) => (
                    <div
                      key={key}
                      className="grid gap-1 text-xs sm:grid-cols-[minmax(0,12rem)_1fr]"
                    >
                      <span className="font-mono break-all text-muted-foreground">{key}</span>
                      <span className="break-all whitespace-pre-wrap">
                        {formatAuditValue(value)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
