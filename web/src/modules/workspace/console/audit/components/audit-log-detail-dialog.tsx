import type { AuditRecord } from "@pudans-dev/api-client";
import Link from "next/link";

import { InfoRow } from "@/components/info-row";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useMenuPath } from "@/modules/workspace/lib/permissions";

import { formatAuditExactTime } from "../lib/api";
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

  return (
    <Dialog
      open={Boolean(record)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="flex max-h-[85vh] flex-col overflow-hidden sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="flex flex-wrap items-center gap-2">
            审计日志详情
            {record ? <AuditResultBadge result={record.result} /> : null}
          </DialogTitle>
          <DialogDescription>
            {record
              ? `${auditOperationLabel(record.operation)} · ${formatAuditExactTime(record.timestamp)}`
              : ""}
          </DialogDescription>
        </DialogHeader>

        {record ? (
          <Tabs defaultValue="log" className="flex min-h-0 flex-1 flex-col">
            <TabsList className="w-fit">
              <TabsTrigger value="log">日志</TabsTrigger>
              <TabsTrigger value="context">上下文</TabsTrigger>
              <TabsTrigger value="snapshot">操作快照</TabsTrigger>
            </TabsList>

            <div className="min-h-0 flex-1 overflow-y-auto py-4">
              <TabsContent value="log" className="mt-0 space-y-5">
                <section className="space-y-2">
                  <h3 className="text-sm font-semibold">日志正文</h3>
                  <pre className="overflow-x-auto border bg-muted/40 p-3 text-xs leading-5 break-all whitespace-pre-wrap">
                    {logBody(record)}
                  </pre>
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

              <TabsContent value="snapshot" className="mt-0">
                <pre className="max-h-96 overflow-auto border bg-muted/40 p-3 text-xs leading-5 break-all whitespace-pre-wrap">
                  {formatAuditSnapshot(record.actionSnapshot)}
                </pre>
              </TabsContent>
            </div>
          </Tabs>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
          {traceLink ? (
            <Button asChild>
              <Link href={traceLink}>查看链路</Link>
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function logBody(record: AuditRecord): string {
  const page = record.resourceType === "Page";
  const method = page ? "PAGE" : record.requestMethod || "-";
  const path = page ? record.resourceName || record.requestPath || "-" : record.requestPath || "-";
  return `[${record.timestamp ?? "—"}] "${method} ${path}" ${record.result ?? "-"} ${record.durationMs ?? 0}ms uid=${record.uid || "-"} trace_id=${record.traceId || "-"}`;
}
