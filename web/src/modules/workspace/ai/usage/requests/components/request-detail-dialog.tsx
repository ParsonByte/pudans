import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";

import { Badge } from "@/components/ui/badge";
import {
  CodeBlock,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import {
  formatDuration,
  formatTokenCount,
  requestStatusLabel,
} from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { UsageErrorState } from "../../components/usage-state";
import { fetchUsageRequestDetail, resolveUsageApiError } from "../../lib/api";
import type { UsageRequestDetail } from "../../lib/api";
import {
  billingStatusLabel,
  formatPercent,
  resolveUsageCacheHitRate,
  usageSourceLabel,
} from "../../lib/model";

type RequestDetailDialogProps = {
  requestId: string;
  onClose: () => void;
};

export function RequestDetailDialog({ requestId, onClose }: RequestDetailDialogProps) {
  const detailQuery = useQuery({
    queryKey: ["ai-usage-request", requestId],
    queryFn: () => fetchUsageRequestDetail(requestId),
    retry: false,
  });
  const detail = detailQuery.data;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>请求详情</DialogTitle>
          <DialogDescription className="font-mono text-xs break-all">{requestId}</DialogDescription>
        </DialogHeader>
        {detailQuery.isError ? (
          <UsageErrorState
            title="无法加载请求详情"
            message={resolveUsageApiError(detailQuery.error)}
            onRetry={() => void detailQuery.refetch()}
          />
        ) : detailQuery.isPending ? (
          <DetailSkeleton />
        ) : detail ? (
          <RequestDetailBody detail={detail} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function RequestDetailBody({ detail }: { detail: UsageRequestDetail }) {
  return (
    <Tabs defaultValue="overview" className="gap-4">
      <TabsList>
        <TabsTrigger value="overview">概览</TabsTrigger>
        <TabsTrigger value="billing">计费</TabsTrigger>
        <TabsTrigger value="trace">轨迹</TabsTrigger>
      </TabsList>
      <TabsContent value="overview" className="max-h-[60vh] space-y-4 overflow-y-auto pe-1">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <DetailField label="Agent" value={detail.agent || "—"} />
          <DetailField label="Agent 来源" value={detail.agentSource || "—"} />
          <DetailField label="模式" value={detail.mode || "—"} />
          <DetailField label="路由组" value={detail.routeGroup || "—"} />
          <DetailField label="供应商" value={detail.provider || "—"} />
          <DetailField label="账号" value={detail.account || "—"} />
          <DetailField label="模型" value={detail.model || "—"} />
          <DetailField label="层级" value={detail.tier || "—"} />
          <DetailField
            label="状态"
            value={
              <span className="inline-flex flex-wrap items-center gap-2">
                <Badge variant={statusVariant(detail.status)}>
                  {requestStatusLabel(detail.status)}
                </Badge>
                {detail.errorCode ? (
                  <span className="font-mono text-xs text-destructive">{detail.errorCode}</span>
                ) : null}
              </span>
            }
          />
          <DetailField label="用户" value={detail.userId || "—"} />
          <DetailField label="团队" value={detail.teamId || "—"} />
          <DetailField label="会话 ID" value={detail.conversationId ?? "—"} />
          <DetailField label="会话键" value={detail.sessionKey || "—"} mono />
          <DetailField label="供应商" value={detail.vendor || "—"} />
          <DetailField
            label="流式"
            value={detail.stream == null ? "—" : detail.stream ? "是" : "否"}
          />
          <DetailField label="服务档位" value={detail.servedEffort || "—"} />
          <DetailField
            label="重试"
            value={detail.retried == null ? "—" : detail.retried ? "是" : "否"}
          />
          <DetailField label="创建时间" value={formatUserDateTime(detail.createdAt)} />
          <DetailField
            label="完成时间"
            value={formatUserDateTime(detail.completedAt ?? undefined)}
          />
        </div>

        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <Metric label="输入 Token" value={formatTokenCount(detail.inputTokens ?? undefined)} />
          <Metric label="缓存读 Token" value={formatTokenCount(detail.cacheTokens ?? undefined)} />
          <Metric
            label="缓存写 Token"
            value={formatTokenCount(detail.cacheWriteTokens ?? undefined)}
          />
          <Metric label="输出 Token" value={formatTokenCount(detail.outputTokens ?? undefined)} />
          <Metric label="总 Token" value={formatTokenCount(detail.totalTokens ?? undefined)} />
          <Metric
            label="缓存命中率"
            value={formatPercent(
              resolveUsageCacheHitRate({
                cacheHitRate: detail.cacheHitRate,
                inputTokens: detail.inputTokens ?? 0,
                cacheTokens: detail.cacheReadTokens ?? detail.cacheTokens ?? 0,
              }),
            )}
          />
          <Metric label="标准 Token" value={formatNumber(detail.units ?? undefined)} />
          <Metric
            label="延迟"
            value={detail.latencyMs == null ? "—" : formatDuration(detail.latencyMs)}
          />
          <Metric
            label="首 Token"
            value={detail.ttftMs == null ? "—" : formatDuration(detail.ttftMs)}
          />
          <Metric
            label="吞吐"
            value={detail.tps == null ? "—" : `${formatNumber(detail.tps, 1)} token/秒`}
          />
        </div>
      </TabsContent>

      <TabsContent value="billing" className="max-h-[60vh] space-y-4 overflow-y-auto pe-1">
        <div className="grid grid-cols-2 gap-4">
          <DetailField label="计费来源" value={usageSourceLabel(detail.billing.source)} />
          <DetailField
            label="计费状态"
            value={<Badge variant="outline">{billingStatusLabel(detail.billing.status)}</Badge>}
          />
          <DetailField label="用量来源" value={usageSourceLabel(detail.billing.usageSource)} />
          <DetailField label="上游请求 ID" value={detail.billing.upstreamRequestId || "—"} mono />
          <DetailField label="标准 Token 合计" value={formatNumber(detail.units ?? undefined)} />
        </div>
        <p className="text-xs text-muted-foreground">
          计费状态与上游请求信息由网关结算流程写入，用量以已结算记录为准。
        </p>
      </TabsContent>

      <TabsContent value="trace" className="max-h-[60vh] space-y-4 overflow-y-auto pe-1">
        <JsonTrace
          title="路由轨迹"
          value={detail.routeTrace}
          emptyText="该请求没有记录路由轨迹。"
        />
        <JsonTrace
          title="检索轨迹"
          value={detail.retrievalTrace}
          emptyText="该请求没有记录检索轨迹。"
        />
        <p className="text-xs text-muted-foreground">
          轨迹字段由网关按请求写入，未经过的路由或检索阶段不会产生记录。
        </p>
      </TabsContent>
    </Tabs>
  );
}

function statusVariant(status: string): "secondary" | "destructive" | "outline" {
  if (status === "FAILED") {
    return "destructive";
  }
  if (status === "RUNNING") {
    return "outline";
  }
  return "secondary";
}

function DetailField({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className={`text-sm break-all ${mono ? "font-mono text-xs" : ""}`}>{value}</p>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="border bg-muted/30 px-3 py-2">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className="mt-1 text-sm font-medium tabular-nums">{value}</p>
    </div>
  );
}

function JsonTrace({
  title,
  value,
  emptyText,
}: {
  title: string;
  value: unknown;
  emptyText: string;
}) {
  const text = value == null ? "" : (JSON.stringify(value, null, 2) ?? "");
  if (!text) {
    return (
      <div className="space-y-2">
        <p className="text-xs font-medium">{title}</p>
        <ConsoleEmpty>{emptyText}</ConsoleEmpty>
      </div>
    );
  }
  return (
    <CodeBlock size="sm">
      <CodeBlockHeader>
        <CodeBlockTitle>{title}</CodeBlockTitle>
        <CodeBlockCopyButton value={text} />
      </CodeBlockHeader>
      <CodeBlockContent className="max-h-56">{text}</CodeBlockContent>
    </CodeBlock>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-8 w-full" />
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {Array.from({ length: 9 }, (_, index) => (
          <Skeleton key={index} className="h-10 w-full" />
        ))}
      </div>
      <Skeleton className="h-24 w-full" />
    </div>
  );
}
