import { Trash2 } from "lucide-react";

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

import type { AiGatewaySession } from "../lib/api";
import { sessionStatusBadgeVariant, sessionStatusLabel } from "../lib/model";

type GatewaySessionTableProps = {
  sessions: AiGatewaySession[];
  canDelete: boolean;
  onDelete: (session: AiGatewaySession) => void;
};

export function GatewaySessionTable({ sessions, canDelete, onDelete }: GatewaySessionTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-4">ID</TableHead>
          <TableHead className="px-4">来源</TableHead>
          <TableHead className="px-4">会话键</TableHead>
          <TableHead className="px-4">会话 ID</TableHead>
          <TableHead className="px-4">团队</TableHead>
          <TableHead className="px-4">Agent</TableHead>
          <TableHead className="px-4">对话</TableHead>
          <TableHead className="px-4">状态</TableHead>
          <TableHead className="px-4">更新时间</TableHead>
          <TableHead className="px-4 text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sessions.map((session) => (
          <TableRow key={session.id}>
            <TableCell className="px-4 font-mono text-xs">#{session.id}</TableCell>
            <TableCell className="px-4">
              <p className="text-sm">{agentLabel(session.agentSource)}</p>
              <p className="font-mono text-[11px] text-muted-foreground">{session.agentSource}</p>
            </TableCell>
            <TableCell className="max-w-56 px-4">
              <LongText className="font-mono text-xs">{session.sessionKey}</LongText>
            </TableCell>
            <TableCell className="max-w-44 px-4">
              <LongText className="font-mono text-xs">{session.sessionId || "—"}</LongText>
            </TableCell>
            <TableCell className="max-w-36 px-4">
              <LongText>{session.teamId}</LongText>
            </TableCell>
            <TableCell className="max-w-36 px-4">
              <LongText>{session.agentId}</LongText>
            </TableCell>
            <TableCell className="px-4 font-mono text-xs">
              {session.conversationId == null ? "—" : `#${session.conversationId}`}
            </TableCell>
            <TableCell className="px-4">
              <Badge variant={sessionStatusBadgeVariant(session.status)}>
                {sessionStatusLabel(session.status)}
              </Badge>
            </TableCell>
            <TableCell className="px-4">
              <p className="text-sm">{formatRelativeTime(session.updatedAt)}</p>
              <p className="text-[11px] text-muted-foreground">
                {formatUserDateTime(session.updatedAt)}
              </p>
            </TableCell>
            <TableCell className="px-4 text-right">
              {canDelete ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="text-destructive hover:text-destructive"
                  aria-label="删除网关会话"
                  title="删除网关会话"
                  onClick={() => onDelete(session)}
                >
                  <Trash2 />
                </Button>
              ) : (
                <span className="text-muted-foreground">—</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
