import { useQuery } from "@tanstack/react-query";

import { LongText } from "@/components/data-table";
import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { agentLabel } from "@/modules/workspace/ai/routing/lib/model";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import { type ChatConversation, fetchConversation, resolveAiSessionApiError } from "../lib/api";
import {
  conversationModeLabel,
  conversationTitle,
  sessionStatusBadgeVariant,
  sessionStatusLabel,
} from "../lib/model";
import { ConversationMessageList } from "./conversation-message-list";

type ConversationDetailDialogProps = {
  conversation: ChatConversation | null;
  onClose: () => void;
};

export function ConversationDetailDialog({ conversation, onClose }: ConversationDetailDialogProps) {
  const conversationId = conversation?.id ?? null;
  const detailQuery = useQuery({
    queryKey: ["ai-sessions", "conversation", conversationId ?? "none"],
    queryFn: () => fetchConversation(conversationId ?? 0),
    enabled: conversationId != null,
    retry: false,
  });
  const detail = detailQuery.data ?? conversation;

  return (
    <DetailDialog
      open={conversation !== null}
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={detail ? conversationTitle(detail) : "会话详情"}
      description={
        detail
          ? `会话 #${detail.id} · ${agentLabel(detail.agentSource)} · ${conversationModeLabel(detail.modeCode)}`
          : undefined
      }
      size="4xl"
    >
      {detail ? (
        <div className="flex min-h-0 flex-1 flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <InfoRow
              label="状态"
              value={
                <Badge variant={sessionStatusBadgeVariant(detail.status)}>
                  {sessionStatusLabel(detail.status)}
                </Badge>
              }
            />
            <InfoRow label="模式" value={conversationModeLabel(detail.modeCode)} />
            <InfoRow label="路由组" value={detail.routeGroup || "—"} />
            <InfoRow label="待处理消息" value={detail.pendingCount} />
            <InfoRow label="最后活动" value={formatUserDateTime(detail.lastActivityAt)} />
            <InfoRow label="创建时间" value={formatUserDateTime(detail.createdAt)} />
            <InfoRow label="团队" value={<LongText>{detail.teamId}</LongText>} />
            <InfoRow
              label="Agent"
              value={
                <div className="space-y-0.5">
                  <LongText>{detail.agentId}</LongText>
                  <p className="text-xs text-muted-foreground">{agentLabel(detail.agentSource)}</p>
                </div>
              }
            />
            <InfoRow label="用户" value={<LongText>{detail.userId}</LongText>} />
            <InfoRow
              className="sm:col-span-2 lg:col-span-3"
              label="会话键"
              value={<LongText className="font-mono text-xs">{detail.sessionKey}</LongText>}
            />
          </div>

          {detailQuery.isError ? (
            <div className="flex flex-wrap items-center justify-between gap-2 border border-destructive/30 bg-destructive/5 px-3 py-2 text-xs text-destructive">
              <span className="min-w-0">
                无法刷新会话信息：{resolveAiSessionApiError(detailQuery.error)}
              </span>
              <Button
                type="button"
                variant="outline"
                size="xs"
                onClick={() => void detailQuery.refetch()}
              >
                重试
              </Button>
            </div>
          ) : null}

          <Separator />

          <ConversationMessageList conversationId={detail.id} />
        </div>
      ) : null}
    </DetailDialog>
  );
}
