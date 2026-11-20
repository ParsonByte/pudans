import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Progress } from "@/components/ui/progress";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { UsageContextGridItem } from "../../lib/api";
import { formatPercent } from "../../lib/model";
import {
  CONTEXT_WINDOW_PARTS,
  contextHealth,
  contextHealthBadgeVariant,
  contextHealthLabel,
  contextWindowTokens,
} from "../lib/context";

type ContextRequestListProps = {
  items: UsageContextGridItem[];
  selectedRequestId: string | null;
  onSelect: (requestId: string) => void;
  onOpen: (requestId: string) => void;
};

export function ContextRequestList({
  items,
  selectedRequestId,
  onSelect,
  onOpen,
}: ContextRequestListProps) {
  if (items.length === 0) {
    return <ConsoleEmpty>当前筛选条件下没有逐请求上下文数据。</ConsoleEmpty>;
  }
  return (
    <div className="max-h-[480px] overflow-y-auto">
      <Table>
        <TableHeader className="sticky top-0 z-10 bg-background">
          <TableRow>
            <TableHead className="px-5">时间</TableHead>
            <TableHead className="px-5">请求 ID</TableHead>
            <TableHead className="px-5">Agent</TableHead>
            <TableHead className="px-5">模型</TableHead>
            <TableHead className="px-5 text-end">上下文 Token</TableHead>
            <TableHead className="px-5 text-end">窗口</TableHead>
            <TableHead className="px-5">利用率</TableHead>
            <TableHead className="px-5">构成</TableHead>
            <TableHead className="px-5 text-end">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((item) => {
            const health = contextHealth(item.utilization);
            const total = contextWindowTokens(item.parts);
            return (
              <TableRow
                key={item.requestId}
                className={`cursor-pointer ${
                  selectedRequestId === item.requestId ? "bg-muted/50" : ""
                }`}
                onClick={() => onSelect(item.requestId)}
              >
                <TableCell className="px-5 py-3 text-xs text-muted-foreground tabular-nums">
                  {formatUserDateTime(item.createdAt)}
                </TableCell>
                <TableCell className="px-5 py-3">
                  <LongText className="max-w-40 font-mono text-xs">{item.requestId}</LongText>
                </TableCell>
                <TableCell className="px-5 py-3 text-sm">{item.agent || "—"}</TableCell>
                <TableCell className="px-5 py-3 text-sm">{item.model || "—"}</TableCell>
                <TableCell className="px-5 py-3 text-end text-sm tabular-nums">
                  {formatTokenCount(item.inputTokens)}
                </TableCell>
                <TableCell className="px-5 py-3 text-end text-xs text-muted-foreground tabular-nums">
                  {item.contextWindow == null ? "—" : formatTokenCount(item.contextWindow)}
                </TableCell>
                <TableCell className="px-5 py-3">
                  <div className="flex items-center gap-2">
                    <Progress
                      value={
                        item.utilization == null
                          ? 0
                          : Math.min(100, Math.max(0, item.utilization * 100))
                      }
                      className="h-1.5 w-14"
                      aria-label="上下文窗口利用率"
                    />
                    <Badge variant={contextHealthBadgeVariant(health)}>
                      {formatPercent(item.utilization)}
                    </Badge>
                  </div>
                </TableCell>
                <TableCell className="px-5 py-3">
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className="flex h-2.5 w-24 overflow-hidden bg-muted"
                        aria-label="查看上下文构成"
                        onClick={(event) => event.stopPropagation()}
                      >
                        {CONTEXT_WINDOW_PARTS.map((part) => {
                          const tokens = Math.max(item.parts[part.key], 0);
                          const width = total > 0 ? (tokens / total) * 100 : 0;
                          if (width <= 0) {
                            return null;
                          }
                          return (
                            <span
                              key={part.key}
                              style={{
                                width: `${width}%`,
                                backgroundColor: part.color,
                              }}
                            />
                          );
                        })}
                      </button>
                    </PopoverTrigger>
                    <PopoverContent className="w-64">
                      <p className="font-mono text-[11px] break-all">{item.requestId}</p>
                      <div className="mt-2 space-y-1.5">
                        {CONTEXT_WINDOW_PARTS.map((part) => (
                          <div
                            key={part.key}
                            className="flex items-center justify-between gap-3 text-xs"
                          >
                            <span className="inline-flex items-center gap-1.5">
                              <span className="size-2" style={{ backgroundColor: part.color }} />
                              {part.label}
                            </span>
                            <span className="tabular-nums">
                              {formatTokenCount(item.parts[part.key])}
                            </span>
                          </div>
                        ))}
                      </div>
                      <p className="mt-2 border-t pt-2 text-[10px] text-muted-foreground">
                        上下文合计 {formatTokenCount(total)} · {contextHealthLabel(health)} · 输出{" "}
                        {formatTokenCount(item.outputTokens)} Token 不计入窗口。
                      </p>
                    </PopoverContent>
                  </Popover>
                </TableCell>
                <TableCell className="px-5 py-3 text-end">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={(event) => {
                      event.stopPropagation();
                      onOpen(item.requestId);
                    }}
                  >
                    详情
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
