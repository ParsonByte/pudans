import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { UsageErrorState } from "../../components/usage-state";
import { fetchUsageSessionDetail, resolveUsageApiError } from "../../lib/api";
import type { UsageSessionDetail } from "../../lib/api";
import { formatSessionDuration, messageRoleLabel, sessionStatusLabel } from "../../lib/model";

type SessionDetailDialogProps = {
  conversationId: number;
  onClose: () => void;
};

export function SessionDetailDialog({ conversationId, onClose }: SessionDetailDialogProps) {
  const detailQuery = useQuery({
    queryKey: ["ai-usage-session", conversationId],
    queryFn: () => fetchUsageSessionDetail(conversationId),
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
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{detail?.conversation.title || "会话详情"}</DialogTitle>
          <DialogDescription>会话 #{conversationId}</DialogDescription>
        </DialogHeader>
        {detailQuery.isError ? (
          <UsageErrorState
            title="无法加载会话详情"
            message={resolveUsageApiError(detailQuery.error)}
            onRetry={() => void detailQuery.refetch()}
          />
        ) : detailQuery.isPending ? (
          <SessionDetailSkeleton />
        ) : detail ? (
          <SessionDetailBody detail={detail} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function SessionDetailBody({ detail }: { detail: UsageSessionDetail }) {
  const conversation = detail.conversation;

  return (
    <div className="max-h-[70vh] space-y-5 overflow-y-auto pe-1">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <DetailField label="Agent" value={conversation.agent || "—"} />
        <DetailField label="Agent 来源" value={conversation.agentSource || "—"} />
        <DetailField label="模型" value={conversation.model || "—"} />
        <DetailField
          label="状态"
          value={
            <Badge variant={conversation.status === "ACTIVE" ? "secondary" : "outline"}>
              {sessionStatusLabel(conversation.status)}
            </Badge>
          }
        />
        <DetailField label="用户" value={conversation.userId || "—"} />
        <DetailField label="团队" value={conversation.teamId || "—"} />
        <DetailField label="创建时间" value={formatUserDateTime(conversation.createdAt)} />
        <DetailField label="最后活动" value={formatUserDateTime(conversation.lastActivityAt)} />
        <DetailField label="会话时长" value={formatSessionDuration(conversation.durationSeconds)} />
      </div>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Metric label="消息总数" value={formatNumber(detail.messageCount)} />
        <Metric label="用户消息" value={formatNumber(detail.userMessageCount)} />
        <Metric label="助手消息" value={formatNumber(detail.assistantMessageCount)} />
        <Metric label="请求数" value={formatNumber(conversation.requestCount)} />
        <Metric label="输入 Token" value={formatTokenCount(conversation.inputTokens)} />
        <Metric label="缓存 Token" value={formatTokenCount(conversation.cacheTokens)} />
        <Metric label="输出 Token" value={formatTokenCount(conversation.outputTokens)} />
        <Metric label="标准 Token" value={formatTokenCount(conversation.units)} />
      </div>

      <section className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">最近消息</h3>
          <span className="text-[11px] text-muted-foreground">最新在前，最多 10 条</span>
        </div>
        {detail.recentMessages.length === 0 ? (
          <ConsoleEmpty>该会话暂无消息。</ConsoleEmpty>
        ) : (
          <ScrollArea className="h-72 pe-3">
            <div className="space-y-3">
              {detail.recentMessages.map((message) => (
                <div key={message.messageId} className="space-y-1.5 border bg-muted/30 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={message.role === "assistant" ? "default" : "outline"}>
                      {messageRoleLabel(message.role)}
                    </Badge>
                    <span className="text-[11px] text-muted-foreground tabular-nums">
                      {formatUserDateTime(message.createdAt)}
                    </span>
                    {message.model ? (
                      <span className="font-mono text-[10px] text-muted-foreground">
                        {message.model}
                      </span>
                    ) : null}
                    {message.tokens != null ? (
                      <span className="ms-auto text-[11px] text-muted-foreground tabular-nums">
                        {formatTokenCount(message.tokens ?? undefined)} Token
                      </span>
                    ) : null}
                  </div>
                  <p className="text-sm leading-6 break-words whitespace-pre-wrap">
                    {message.content || "（空消息）"}
                  </p>
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
      </section>
    </div>
  );
}

function DetailField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0 space-y-1">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <div className="text-sm break-all">{value}</div>
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

function SessionDetailSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {Array.from({ length: 9 }, (_, index) => (
          <Skeleton key={index} className="h-10 w-full" />
        ))}
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {Array.from({ length: 8 }, (_, index) => (
          <Skeleton key={index} className="h-14 w-full" />
        ))}
      </div>
      <Skeleton className="h-48 w-full" />
    </div>
  );
}
