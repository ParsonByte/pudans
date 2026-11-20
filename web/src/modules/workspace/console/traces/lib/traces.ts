import type { AuditRecord } from "@pudans-dev/api-client";

import { formatBeijingDateTimeMillis } from "@/lib/date";

export type TraceSpanKind = "ENTRY" | "CHILD" | "ORPHAN";

type TraceSpanRow = {
  record: AuditRecord;
  kind: TraceSpanKind;
  startMs: number;
  endMs: number;
  durationMs: number;
};

export type TraceSpanNode = TraceSpanRow & {
  parentId?: string;
  depth: number;
  offsetMs: number;
  relativeOffset: number;
  relativeWidth: number;
  children: TraceSpanNode[];
};

type TraceCompleteness = {
  complete: boolean;
  entrySpanCount: number;
  orphanSpanCount: number;
  issues: string[];
};

export type TraceTree = {
  roots: TraceSpanNode[];
  spans: TraceSpanNode[];
  startMs: number;
  endMs: number;
  totalMs: number;
  traceIds: string[];
  spanCount: number;
  completeness: TraceCompleteness;
};

const SPAN_KIND_LABELS: Record<TraceSpanKind, string> = {
  ENTRY: "入口 Span",
  CHILD: "子 Span",
  ORPHAN: "孤立 Span",
};

export const SPAN_KIND_BADGE_VARIANTS: Record<
  TraceSpanKind,
  "secondary" | "outline" | "destructive"
> = {
  ENTRY: "secondary",
  CHILD: "outline",
  ORPHAN: "destructive",
};

export function spanKindLabel(kind: TraceSpanKind): string {
  return SPAN_KIND_LABELS[kind];
}

function timestampOf(record: AuditRecord): number {
  if (!record.timestamp) {
    return 0;
  }
  const value = Date.parse(record.timestamp);
  return Number.isNaN(value) ? 0 : value;
}

function durationOf(record: AuditRecord): number {
  return Math.max(0, record.durationMs ?? 0);
}

function startOf(record: AuditRecord): number {
  return Math.max(0, timestampOf(record) - durationOf(record));
}

function endOf(record: AuditRecord): number {
  return timestampOf(record);
}

function idOf(record: AuditRecord): string {
  return record.metadata.name;
}

function parentIdOf(record: AuditRecord): string | undefined {
  const parentId = record.parentAuditId?.trim();
  return parentId ? parentId : undefined;
}

function sortRecords(records: readonly AuditRecord[]): AuditRecord[] {
  return [...records].sort(
    (left, right) =>
      startOf(left) - startOf(right) ||
      (left.stepIndex ?? Number.MAX_SAFE_INTEGER) - (right.stepIndex ?? Number.MAX_SAFE_INTEGER) ||
      idOf(left).localeCompare(idOf(right)),
  );
}

function resolveKind(record: AuditRecord, byId: Map<string, AuditRecord>): TraceSpanKind {
  const parentId = parentIdOf(record);
  if (!parentId) {
    return "ENTRY";
  }
  const visited = new Set<string>([idOf(record)]);
  let current = byId.get(parentId);
  while (current) {
    const currentId = idOf(current);
    if (visited.has(currentId)) {
      return "ORPHAN";
    }
    visited.add(currentId);
    const currentParentId = parentIdOf(current);
    if (!currentParentId) {
      return "CHILD";
    }
    current = byId.get(currentParentId);
  }
  return "ORPHAN";
}

function buildTraceSpanRows(
  records: readonly AuditRecord[],
  order: "asc" | "desc" = "asc",
): TraceSpanRow[] {
  const sorted = sortRecords(records);
  if (order === "desc") {
    sorted.reverse();
  }
  const byId = new Map(records.map((record) => [idOf(record), record]));
  return sorted.map((record) => ({
    record,
    kind: resolveKind(record, byId),
    startMs: startOf(record),
    endMs: endOf(record),
    durationMs: durationOf(record),
  }));
}

export function buildTraceTree(records: readonly AuditRecord[]): TraceTree {
  const sorted = sortRecords(records);
  if (sorted.length === 0) {
    return {
      roots: [],
      spans: [],
      startMs: 0,
      endMs: 0,
      totalMs: 1,
      traceIds: [],
      spanCount: 0,
      completeness: {
        complete: false,
        entrySpanCount: 0,
        orphanSpanCount: 0,
        issues: ["没有 Span 数据"],
      },
    };
  }

  const startMs = Math.min(...sorted.map(startOf));
  const endMs = Math.max(...sorted.map(endOf));
  const totalMs = Math.max(1, endMs - startMs);
  const rows = buildTraceSpanRows(sorted);
  const nodes = new Map<string, TraceSpanNode>();

  for (const row of rows) {
    nodes.set(idOf(row.record), {
      ...row,
      parentId: parentIdOf(row.record),
      depth: 0,
      offsetMs: 0,
      relativeOffset: 0,
      relativeWidth: 0,
      children: [],
    });
  }

  const roots: TraceSpanNode[] = [];
  for (const row of rows) {
    const node = nodes.get(idOf(row.record));
    if (!node) {
      continue;
    }
    const parent = node.parentId ? nodes.get(node.parentId) : undefined;
    if (row.kind === "CHILD" && parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  const spans: TraceSpanNode[] = [];
  const visited = new Set<string>();

  const visit = (node: TraceSpanNode, depth: number) => {
    const id = idOf(node.record);
    if (visited.has(id)) {
      return;
    }
    visited.add(id);
    node.depth = depth;
    node.offsetMs = Math.max(0, node.startMs - startMs);
    node.relativeOffset = Math.min(100, (node.offsetMs / totalMs) * 100);
    node.relativeWidth = Math.min(
      100 - node.relativeOffset,
      Math.max((node.durationMs / totalMs) * 100, 0.6),
    );
    spans.push(node);
    for (const child of node.children) {
      visit(child, depth + 1);
    }
  };

  roots.sort((left, right) => left.startMs - right.startMs);
  for (const root of roots) {
    visit(root, 0);
  }

  const entrySpanCount = rows.filter((row) => row.kind === "ENTRY").length;
  const orphanSpanCount = rows.filter((row) => row.kind === "ORPHAN").length;
  const issues: string[] = [];
  if (entrySpanCount === 0) {
    issues.push("缺少入口 Span");
  } else if (entrySpanCount > 1) {
    issues.push(`存在 ${entrySpanCount} 个入口 Span`);
  }
  if (orphanSpanCount > 0) {
    issues.push(`${orphanSpanCount} 个 Span 未接入链路`);
  }

  const traceIds = [...new Set(sorted.map((record) => record.traceId).filter(Boolean))].filter(
    (value): value is string => Boolean(value),
  );

  return {
    roots,
    spans,
    startMs,
    endMs,
    totalMs,
    traceIds,
    spanCount: spans.length,
    completeness: {
      complete: issues.length === 0,
      entrySpanCount,
      orphanSpanCount,
      issues,
    },
  };
}

export function formatDuration(value?: number): string {
  if (!value || value <= 0) {
    return "0 ms";
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)} s`;
  }
  return `${value} ms`;
}

export function formatTraceTime(value: number): string {
  return formatBeijingDateTimeMillis(new Date(value));
}

export function formatTraceAxis(valueMs: number): string {
  if (valueMs >= 1000) {
    return `${(valueMs / 1000).toFixed(2)} s`;
  }
  return `${Math.round(valueMs)} ms`;
}
