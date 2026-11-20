import { LongText } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { UsageSessionStatsTopSession } from "../../lib/api";

type SessionTopTableProps = {
  sessions: UsageSessionStatsTopSession[];
  onOpen: (conversationId: number) => void;
};

export function SessionTopTable({ sessions, onOpen }: SessionTopTableProps) {
  if (sessions.length === 0) {
    return <ConsoleEmpty>所选时间范围内没有会话记录。</ConsoleEmpty>;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-5">会话</TableHead>
          <TableHead className="px-5">Agent</TableHead>
          <TableHead className="px-5">模型</TableHead>
          <TableHead className="px-5 text-end">消息数</TableHead>
          <TableHead className="px-5 text-end">Tokens</TableHead>
          <TableHead className="px-5 text-end">标准 Token</TableHead>
          <TableHead className="px-5">最后活动</TableHead>
          <TableHead className="px-5 text-end">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sessions.map((session) => (
          <TableRow
            key={session.conversationId}
            className="cursor-pointer"
            onClick={() => onOpen(session.conversationId)}
          >
            <TableCell className="px-5 py-3">
              <LongText className="max-w-56 text-sm font-medium">
                {session.title || "未命名会话"}
              </LongText>
              <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">
                #{session.conversationId}
              </p>
            </TableCell>
            <TableCell className="px-5 py-3 text-sm">{session.agent || "—"}</TableCell>
            <TableCell className="px-5 py-3 text-sm">{session.model || "—"}</TableCell>
            <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
              {formatNumber(session.messages)}
            </TableCell>
            <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
              {formatTokenCount(session.tokens)}
            </TableCell>
            <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
              {formatTokenCount(session.units)}
            </TableCell>
            <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
              {formatUserDateTime(session.lastActivityAt)}
            </TableCell>
            <TableCell className="px-5 py-3 text-end">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={(event) => {
                  event.stopPropagation();
                  onOpen(session.conversationId);
                }}
              >
                查看
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
