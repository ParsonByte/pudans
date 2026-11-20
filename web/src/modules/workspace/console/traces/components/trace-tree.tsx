import { useMemo, useState } from "react";

import { ChevronDown, ChevronRight } from "lucide-react";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { AuditResultBadge } from "@/modules/workspace/console/audit/components/audit-result-badge";
import {
  auditOperationLabel,
  auditResourceLabel,
} from "@/modules/workspace/console/audit/lib/audit-labels";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";

import {
  SPAN_KIND_BADGE_VARIANTS,
  type TraceSpanNode,
  type TraceTree,
  formatDuration,
  formatTraceAxis,
  formatTraceTime,
  spanKindLabel,
} from "../lib/traces";

const AXIS_TICKS = [0, 25, 50, 75, 100] as const;

type TraceTreeProps = {
  tree: TraceTree;
  onSelect: (span: TraceSpanNode, parent?: TraceSpanNode) => void;
  className?: string;
};

export function TraceTree({ tree, onSelect, className }: TraceTreeProps) {
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(() => new Set());
  const collapsibleIds = useMemo(
    () => tree.spans.filter((span) => span.children.length > 0).map(nodeId),
    [tree],
  );
  const allCollapsed =
    collapsibleIds.length > 0 && collapsibleIds.every((id) => collapsedIds.has(id));
  const byId = useMemo(() => new Map(tree.spans.map((span) => [nodeId(span), span])), [tree]);
  const visibleSpans = useMemo(() => {
    const visible: TraceSpanNode[] = [];
    const walk = (span: TraceSpanNode) => {
      visible.push(span);
      if (span.children.length > 0 && !collapsedIds.has(nodeId(span))) {
        span.children.forEach(walk);
      }
    };
    tree.roots.forEach(walk);
    return visible;
  }, [tree, collapsedIds]);

  if (tree.spans.length === 0) {
    return <ConsoleEmpty>该链路没有审计步骤。</ConsoleEmpty>;
  }

  const toggleNode = (id: string) => {
    setCollapsedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleCollapseAll = () => {
    setCollapsedIds(allCollapsed ? new Set() : new Set(collapsibleIds));
  };

  const selectSpan = (span: TraceSpanNode) => {
    const parent = span.parentId ? byId.get(span.parentId) : undefined;
    onSelect(span, parent);
  };

  return (
    <div className={cn("overflow-x-auto", className)}>
      <div className="min-w-[720px]">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b px-5 py-2">
          <span className="text-xs text-muted-foreground">
            {visibleSpans.length} / {tree.spanCount} 个 Span
          </span>
          {collapsibleIds.length > 0 ? (
            <Button type="button" variant="outline" size="sm" onClick={toggleCollapseAll}>
              {allCollapsed ? <ChevronDown /> : <ChevronRight />}
              {allCollapsed ? "展开全部" : "收起全部"}
            </Button>
          ) : null}
        </div>
        <div className="grid grid-cols-[minmax(0,24rem)_1fr] items-center gap-4 border-b px-5 py-2 text-[11px] text-muted-foreground">
          <span>Span / 服务</span>
          <div className="relative h-4">
            {AXIS_TICKS.map((tick) => (
              <span
                key={tick}
                className={cn(
                  "absolute top-0 tabular-nums",
                  tick === 100 ? "-translate-x-full" : tick === 0 ? "" : "-translate-x-1/2",
                )}
                style={{ left: `${tick}%` }}
              >
                {formatTraceAxis((tree.totalMs * tick) / 100)}
              </span>
            ))}
          </div>
        </div>
        {visibleSpans.map((span) => {
          const id = nodeId(span);
          const expanded = !collapsedIds.has(id);
          const hasChildren = span.children.length > 0;
          const failed = span.record.result !== "SUCCESS";
          return (
            <div
              key={id}
              role="button"
              tabIndex={0}
              className="grid cursor-pointer grid-cols-[minmax(0,24rem)_1fr] items-center gap-4 border-b px-5 py-2 transition-colors last:border-b-0 hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none"
              onClick={() => selectSpan(span)}
              onKeyDown={(event) => {
                if (event.target !== event.currentTarget) {
                  return;
                }
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  selectSpan(span);
                }
              }}
            >
              <div
                className="flex min-w-0 items-center gap-1"
                style={{ paddingLeft: `${Math.min(span.depth, 10) * 14}px` }}
              >
                {hasChildren ? (
                  <button
                    type="button"
                    className="flex size-4 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    aria-label={expanded ? "收起子 Span" : "展开子 Span"}
                    onClick={(event) => {
                      event.stopPropagation();
                      toggleNode(id);
                    }}
                  >
                    {expanded ? (
                      <ChevronDown className="size-3.5" />
                    ) : (
                      <ChevronRight className="size-3.5" />
                    )}
                  </button>
                ) : (
                  <span className="size-4 shrink-0" />
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <span className="truncate text-xs font-medium">
                      {auditOperationLabel(span.record.operation)}
                    </span>
                    <Badge variant={SPAN_KIND_BADGE_VARIANTS[span.kind]} className="shrink-0">
                      {spanKindLabel(span.kind)}
                    </Badge>
                    <AuditResultBadge result={span.record.result} />
                    <span className="ms-auto shrink-0 text-[11px] text-muted-foreground tabular-nums">
                      {formatDuration(span.durationMs)}
                    </span>
                  </div>
                  <div className="flex min-w-0 items-center gap-1 text-[11px] text-muted-foreground">
                    <span className="shrink-0">{auditResourceLabel(span.record.resourceType)}</span>
                    {span.record.resourceName ? (
                      <>
                        <span className="shrink-0">·</span>
                        <LongText className="max-w-64">{span.record.resourceName}</LongText>
                      </>
                    ) : null}
                  </div>
                </div>
              </div>
              <div className="relative h-7">
                <div className="absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-border" />
                <div
                  className={cn(
                    "absolute top-1/2 h-3 -translate-y-1/2 rounded",
                    failed
                      ? "bg-destructive"
                      : span.kind === "ORPHAN"
                        ? "bg-warning"
                        : span.kind === "ENTRY"
                          ? "bg-primary"
                          : "bg-primary/60",
                  )}
                  style={{
                    left: `${span.relativeOffset}%`,
                    width: `${span.relativeWidth}%`,
                  }}
                  title={`${formatTraceTime(span.startMs)} → ${formatTraceTime(
                    span.endMs,
                  )} · ${formatDuration(span.durationMs)}`}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function nodeId(span: TraceSpanNode): string {
  return span.record.metadata.name;
}
