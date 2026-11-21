import type { AuditRecord, TraceStep } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";

import { HighlightedCode } from "@/components/content/highlighted-code";
import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CodeBlock,
  CodeBlockActions,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { fetchAuditTrace, formatAuditExactTime } from "../lib/api";
import { formatAuditSnapshot, formatAuditValue } from "../lib/audit-format";
import { auditOperationLabel, auditResourceLabel } from "../lib/audit-labels";
import { AuditResultBadge } from "./audit-result-badge";

type AuditLogDetailDialogProps = {
  record: AuditRecord | null;
  onClose: () => void;
};

export function AuditLogDetailDialog({ record, onClose }: AuditLogDetailDialogProps) {
  const tracesPath = useMenuPath("traces");
  const contextEntries = Object.entries((record?.context ?? {}) as Record<string, unknown>);
  const traceLink =
    record?.traceFlowId && tracesPath
      ? `${tracesPath}/${encodeURIComponent(record.traceFlowId)}`
      : null;
  const snapshot = record ? formatAuditSnapshot(record.actionSnapshot) : "";

  return (
    <DetailDialog
      open={Boolean(record)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={
        <span className="flex flex-wrap items-center gap-2">
          审计日志详情
          {record ? <AuditResultBadge result={record.result} /> : null}
        </span>
      }
      description={
        record
          ? `${auditOperationLabel(record.operation)} · ${formatAuditExactTime(record.timestamp)}`
          : ""
      }
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
          {traceLink ? (
            <Button asChild>
              <Link href={traceLink}>查看链路</Link>
            </Button>
          ) : null}
        </>
      }
    >
      {record ? (
        <Tabs defaultValue="log" className="gap-4">
          <TabsList className="w-fit">
            <TabsTrigger value="log">日志</TabsTrigger>
            <TabsTrigger value="context">上下文</TabsTrigger>
            <TabsTrigger value="trace">链路</TabsTrigger>
            <TabsTrigger value="snapshot">操作快照</TabsTrigger>
          </TabsList>

          <div className="py-4">
            <TabsContent value="log" className="mt-0 space-y-5">
              <section className="space-y-2">
                <h3 className="text-sm font-semibold">日志正文</h3>
                <CodeBlock>
                  <CodeBlockContent wrap className="text-xs leading-5">
                    {logBody(record)}
                  </CodeBlockContent>
                </CodeBlock>
              </section>

              <section className="space-y-2">
                <h3 className="text-sm font-semibold">日志属性</h3>
                <div className="grid gap-4 border p-3 sm:grid-cols-2">
                  <InfoRow label="操作" value={auditOperationLabel(record.operation)} />
                  <InfoRow label="结果" value={<AuditResultBadge result={record.result} />} />
                  <InfoRow label="用户 UID" value={record.uid || "—"} />
                  <InfoRow
                    label="审计 ID"
                    value={
                      <span className="font-mono text-xs break-all">{record.metadata.name}</span>
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
                    value={
                      <span className="font-mono text-xs break-all">
                        {record.traceFlowId || "—"}
                      </span>
                    }
                  />
                  <InfoRow
                    label="父审计 ID"
                    value={
                      <span className="font-mono text-xs break-all">
                        {record.parentAuditId || "—"}
                      </span>
                    }
                  />
                  <InfoRow label="步骤序号" value={record.stepIndex ?? "—"} />
                  <InfoRow label="归档状态" value={record.archived ? "已归档" : "活跃"} />
                </div>
              </section>

              <section className="space-y-2">
                <h3 className="text-sm font-semibold">资源属性</h3>
                <div className="grid gap-4 border p-3 sm:grid-cols-2">
                  <InfoRow label="资源类型" value={auditResourceLabel(record.resourceType)} />
                  <InfoRow
                    label="资源名称"
                    value={
                      <span className="font-mono text-xs break-all">
                        {record.resourceName || "—"}
                      </span>
                    }
                  />
                  <InfoRow label="请求方法" value={record.requestMethod || "—"} />
                  <InfoRow
                    label="请求路径"
                    value={
                      <span className="font-mono text-xs break-all">
                        {record.requestPath || "—"}
                      </span>
                    }
                  />
                  <InfoRow label="IP" value={record.ip || "—"} />
                  <InfoRow
                    label="User-Agent"
                    value={<span className="text-xs break-all">{record.userAgent || "—"}</span>}
                  />
                </div>
              </section>
            </TabsContent>

            <TabsContent value="context" className="mt-0">
              {contextEntries.length === 0 ? (
                <p className="text-xs text-muted-foreground">该记录没有上下文信息。</p>
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
            </TabsContent>

            <TabsContent value="trace" className="mt-0">
              <AuditTracePanel record={record} />
            </TabsContent>

            <TabsContent value="snapshot" className="mt-0">
              <CodeBlock className="max-h-96">
                <CodeBlockHeader>
                  <CodeBlockTitle>操作快照</CodeBlockTitle>
                  <CodeBlockActions>
                    <CodeBlockCopyButton value={snapshot} />
                  </CodeBlockActions>
                </CodeBlockHeader>
                <HighlightedCode code={snapshot} language="json" />
              </CodeBlock>
            </TabsContent>
          </div>
        </Tabs>
      ) : null}
    </DetailDialog>
  );
}

function logBody(record: AuditRecord): string {
  const page = record.resourceType === "Page";
  const method = page ? "PAGE" : record.requestMethod || "-";
  const path = page ? record.resourceName || record.requestPath || "-" : record.requestPath || "-";
  return `[${record.timestamp ?? "—"}] "${method} ${path}" ${record.result ?? "-"} ${record.durationMs ?? 0}ms uid=${record.uid || "-"} trace_id=${record.traceId || "-"}`;
}

function AuditTracePanel({ record }: { record: AuditRecord }) {
  const query = useQuery({
    queryKey: ["audit-trace", record.metadata.name],
    queryFn: () => fetchAuditTrace(record.metadata.name),
    retry: false,
  });

  if (query.isPending) {
    return (
      <ConsoleBlockSkeleton rows={4} className="space-y-2 border p-3" itemClassName="h-10 w-full" />
    );
  }

  if (query.isError) {
    return (
      <ConsoleState
        variant="error"
        title="无法加载链路"
        description="请检查登录状态后重试。"
        onRetry={() => void query.refetch()}
        className="border"
      />
    );
  }

  const trace = query.data;
  const steps = trace?.steps ?? [];
  const before = trace?.before ?? [];
  const after = trace?.after ?? [];

  if (steps.length === 0) {
    return <ConsoleState variant="empty" title="该记录没有链路步骤。" />;
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-4 border p-3 sm:grid-cols-2">
        <InfoRow
          label="Trace Flow ID"
          value={
            <span className="font-mono text-xs break-all">
              {trace?.traceFlowId || record.traceFlowId || "—"}
            </span>
          }
        />
        <InfoRow label="链路步骤" value={`${steps.length} 步`} />
        <InfoRow label="前序步骤" value={`${before.length} 步`} />
        <InfoRow label="后续步骤" value={`${after.length} 步`} />
      </div>

      <div className="space-y-2">
        <h3 className="text-sm font-semibold">链路步骤</h3>
        <ol className="divide-y border">
          {steps.map((step) => (
            <TraceStepRow
              key={step.auditId ?? `${step.stepIndex ?? "?"}-${step.timestamp}`}
              step={step}
            />
          ))}
        </ol>
      </div>
    </div>
  );
}

function TraceStepRow({ step }: { step: TraceStep }) {
  return (
    <li
      className={cn(
        "flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2",
        step.focus && "bg-muted/60",
      )}
    >
      <span className="w-10 shrink-0 text-[11px] text-muted-foreground tabular-nums">
        {step.stepIndex ?? "—"}
      </span>
      {step.focus ? <Badge variant="secondary">焦点</Badge> : null}
      <span className="w-24 shrink-0 text-xs font-medium">
        {auditOperationLabel(step.operation)}
      </span>
      <Badge variant="outline" className="shrink-0">
        {auditResourceLabel(step.resourceType)}
      </Badge>
      <span className="min-w-0 flex-1 truncate font-mono text-xs text-muted-foreground">
        {step.resourceName || step.auditId || "—"}
      </span>
      <AuditResultBadge result={step.result} />
      <span className="w-40 shrink-0 text-end text-[11px] text-muted-foreground tabular-nums">
        {formatAuditExactTime(step.timestamp)}
      </span>
    </li>
  );
}
