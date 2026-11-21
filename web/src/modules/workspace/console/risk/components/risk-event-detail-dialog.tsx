import { Loader2, Unlock } from "lucide-react";

import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

import type { RiskEventItem } from "../lib/api";
import {
  EVENT_LABELS,
  type RiskEventStatus,
  eventActorLabel,
  formatExactTime,
} from "../lib/risk-events";

type RiskEventDetailDialogProps = {
  event: RiskEventItem | null;
  status: RiskEventStatus | null;
  sceneLabel: string;
  canUnban: boolean;
  isUnbanning: boolean;
  onUnban: () => void;
  onClose: () => void;
};

export function RiskEventDetailDialog({
  event,
  status,
  sceneLabel,
  canUnban,
  isUnbanning,
  onUnban,
  onClose,
}: RiskEventDetailDialogProps) {
  return (
    <DetailDialog
      open={Boolean(event)}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="风险事件详情"
      description={event ? `${formatExactTime(event.timestamp)} · ${event.auditId}` : ""}
      size="xl"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose}>
            关闭
          </Button>
          {event && status?.key === "active" && canUnban ? (
            <Button type="button" variant="destructive" disabled={isUnbanning} onClick={onUnban}>
              {isUnbanning ? <Loader2 className="animate-spin" /> : <Unlock />}
              解除封禁
            </Button>
          ) : null}
        </>
      }
    >
      {event ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={event.operation === "RISK_BLOCKED" ? "destructive" : "secondary"}>
              {EVENT_LABELS[event.operation] ?? event.operation}
            </Badge>
            {status ? <Badge variant={status.variant}>{status.label}</Badge> : null}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <InfoRow label="触发用户" value={eventActorLabel(event)} />
            <InfoRow
              label="对象"
              value={<span className="font-mono text-xs">{event.subject || "—"}</span>}
            />
            <InfoRow label="场景" value={`${sceneLabel}（${event.scene}）`} />
            <InfoRow label="请求方法" value={event.requestMethod || "—"} />
            <InfoRow
              label="路径"
              value={<span className="font-mono text-xs">{event.path || "—"}</span>}
              className="sm:col-span-2"
            />
            <InfoRow label="触发次数" value={event.count} />
            <InfoRow label="结果" value={event.result || "—"} />
            {event.blockSeconds > 0 ? (
              <InfoRow label="封禁时长" value={`${event.blockSeconds} 秒`} />
            ) : null}
            {status?.banUntil ? (
              <InfoRow label="封禁到期" value={formatExactTime(status.banUntil)} />
            ) : null}
            {status?.unbanAt ? (
              <InfoRow label="解禁时间" value={formatExactTime(status.unbanAt)} />
            ) : null}
            <InfoRow
              label="审计 ID"
              value={<span className="font-mono text-xs">{event.auditId}</span>}
            />
            {event.traceId ? (
              <InfoRow
                label="Trace ID"
                value={<span className="font-mono text-xs">{event.traceId}</span>}
              />
            ) : null}
          </div>
        </div>
      ) : null}
    </DetailDialog>
  );
}
