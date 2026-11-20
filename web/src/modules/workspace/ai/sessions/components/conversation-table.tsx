import { Eye, Trash2 } from "lucide-react";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { agentLabel, formatRelativeTime } from "@/modules/workspace/ai/routing/lib/model";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { ChatConversation } from "../lib/api";
import {
  conversationModeLabel,
  conversationTitle,
  sessionStatusBadgeVariant,
  sessionStatusLabel,
} from "../lib/model";

type ConversationTableProps = {
  conversations: ChatConversation[];
  canDelete: boolean;
  onOpen: (conversation: ChatConversation) => void;
  onDelete: (conversation: ChatConversation) => void;
};

export function ConversationTable({
  conversations,
  canDelete,
  onOpen,
  onDelete,
}: ConversationTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-4">标题</TableHead>
          <TableHead className="px-4">Agent</TableHead>
          <TableHead className="px-4">模式</TableHead>
          <TableHead className="px-4">路由组</TableHead>
          <TableHead className="px-4">状态</TableHead>
          <TableHead className="px-4 text-right">待处理消息</TableHead>
          <TableHead className="px-4">最后活动</TableHead>
          <TableHead className="px-4">创建时间</TableHead>
          <TableHead className="px-4 text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {conversations.map((conversation) => (
          <TableRow key={conversation.id}>
            <TableCell className="px-4">
              <Button
                type="button"
                variant="ghost"
                className="h-auto max-w-64 flex-col items-start gap-0.5 px-0 text-start hover:bg-transparent"
                onClick={() => onOpen(conversation)}
              >
                <span className="max-w-full truncate text-sm font-medium">
                  {conversationTitle(conversation)}
                </span>
                <span className="text-[11px] font-normal text-muted-foreground">
                  会话 #{conversation.id}
                </span>
              </Button>
            </TableCell>
            <TableCell className="px-4">
              <LongText className="max-w-44 font-medium">{conversation.agentId}</LongText>
              <p className="text-[11px] text-muted-foreground">
                {agentLabel(conversation.agentSource)}
              </p>
            </TableCell>
            <TableCell className="px-4">
              <Badge variant="outline">{conversationModeLabel(conversation.modeCode)}</Badge>
            </TableCell>
            <TableCell className="max-w-40 px-4">
              <LongText>{conversation.routeGroup || "—"}</LongText>
            </TableCell>
            <TableCell className="px-4">
              <Badge variant={sessionStatusBadgeVariant(conversation.status)}>
                {sessionStatusLabel(conversation.status)}
              </Badge>
            </TableCell>
            <TableCell className="px-4 text-right">
              {conversation.pendingCount > 0 ? (
                <Badge variant="secondary">{conversation.pendingCount}</Badge>
              ) : (
                <span className="text-muted-foreground">—</span>
              )}
            </TableCell>
            <TableCell className="px-4">
              <p className="text-sm">{formatRelativeTime(conversation.lastActivityAt)}</p>
              <p className="text-[11px] text-muted-foreground">
                {formatUserDateTime(conversation.lastActivityAt)}
              </p>
            </TableCell>
            <TableCell className="px-4 text-sm">
              {formatUserDateTime(conversation.createdAt)}
            </TableCell>
            <TableCell className="px-4 text-right">
              <div className="flex items-center justify-end gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label="查看会话详情"
                  title="查看会话详情"
                  onClick={() => onOpen(conversation)}
                >
                  <Eye />
                </Button>
                {canDelete ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    className="text-destructive hover:text-destructive"
                    aria-label="删除会话"
                    title="删除会话"
                    onClick={() => onDelete(conversation)}
                  >
                    <Trash2 />
                  </Button>
                ) : null}
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
