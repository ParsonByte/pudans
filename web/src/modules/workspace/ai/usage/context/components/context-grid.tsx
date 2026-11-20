import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { UsageContextGridItem } from "../../lib/api";
import { formatPercent } from "../../lib/model";
import {
  CONTEXT_WINDOW_PARTS,
  contextHealth,
  contextHealthColorClass,
  contextHealthLabel,
} from "../lib/context";

const GRID_SIZE = 400;

const GRID_COLUMNS = 20;

type ContextGridProps = {
  items: UsageContextGridItem[];
  selectedRequestId: string | null;
  onSelect: (requestId: string) => void;
};

export function ContextGrid({ items, selectedRequestId, onSelect }: ContextGridProps) {
  if (items.length === 0) {
    return <ConsoleEmpty>当前筛选条件下没有逐请求上下文数据。</ConsoleEmpty>;
  }
  const cells = Array.from({ length: GRID_SIZE }, (_, index) => items[index] ?? null);

  return (
    <div className="space-y-4 p-5">
      <div
        className="grid gap-1"
        style={{ gridTemplateColumns: `repeat(${GRID_COLUMNS}, minmax(0, 1fr))` }}
      >
        {cells.map((item, index) => {
          if (!item) {
            return (
              <div
                key={`empty-${index}`}
                className="aspect-square rounded-[2px] border border-dashed bg-muted/30"
              />
            );
          }
          const health = contextHealth(item.utilization);
          return (
            <Tooltip key={item.requestId}>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  aria-label={`请求 ${item.requestId}`}
                  onClick={() => onSelect(item.requestId)}
                  className={`aspect-square rounded-[2px] transition-shadow hover:ring-2 hover:ring-ring ${contextHealthColorClass(
                    health,
                  )} ${selectedRequestId === item.requestId ? "ring-2 ring-ring ring-offset-1" : ""}`}
                />
              </TooltipTrigger>
              <TooltipContent className="max-w-72">
                <p className="font-mono text-[11px] break-all">{item.requestId}</p>
                <p className="mt-0.5 text-muted-foreground">
                  {formatUserDateTime(item.createdAt)} · {item.agent || "—"} · {item.model || "—"}
                </p>
                <div className="mt-1.5 flex items-center justify-between gap-3">
                  <span>窗口利用率</span>
                  <span className="font-medium">
                    {formatPercent(item.utilization)} · {contextHealthLabel(health)}
                  </span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-3">
                  <span>上下文 / 窗口</span>
                  <span className="font-medium tabular-nums">
                    {formatTokenCount(item.inputTokens)} /{" "}
                    {item.contextWindow == null ? "—" : formatTokenCount(item.contextWindow)}
                  </span>
                </div>
                <div className="mt-2 space-y-1 border-t border-border/60 pt-2">
                  {CONTEXT_WINDOW_PARTS.map((part) => (
                    <div key={part.key} className="flex items-center justify-between gap-3">
                      <span className="inline-flex items-center gap-1.5">
                        <span
                          className="size-2 rounded-[2px]"
                          style={{ backgroundColor: part.color }}
                        />
                        {part.label}
                      </span>
                      <span className="tabular-nums">{formatTokenCount(item.parts[part.key])}</span>
                    </div>
                  ))}
                </div>
                <p className="mt-1.5 text-[10px] text-muted-foreground">
                  输出 {formatTokenCount(item.outputTokens)} Token 不计入窗口。
                </p>
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-[11px] text-muted-foreground">
        <span className="inline-flex items-center gap-1.5">
          <span className="size-2.5 rounded-[2px] bg-emerald-500" />
          健康 &lt; 75%
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="size-2.5 rounded-[2px] bg-amber-500" />
          关注 75% - 90%
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="size-2.5 rounded-[2px] bg-destructive" />
          高危 ≥ 90%
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="size-2.5 rounded-[2px] bg-muted" />
          无窗口数据
        </span>
        <span>共 {items.length} 个请求，最多展示 400 格。</span>
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-[11px] text-muted-foreground">
        <span className="font-medium text-foreground">窗口构成：</span>
        {CONTEXT_WINDOW_PARTS.map((part) => (
          <span key={part.key} className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-[2px]" style={{ backgroundColor: part.color }} />
            {part.label}
          </span>
        ))}
        <span>输出不计入窗口容量。</span>
      </div>
    </div>
  );
}
