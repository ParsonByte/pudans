import type { ReplayStep } from "@pudans-dev/api-client";

import { auditResourceLabel } from "@/modules/workspace/console/audit/lib/audit-labels";

type ReplayMapNode = {
  id: string;
  label: string;
  kind: "PAGE" | "RESOURCE";
  count: number;
  errorCount: number;
  firstStepIndex: number;
  x: number;
  y: number;
};

type ReplayMapEdge = {
  from: string;
  to: string;
  count: number;
};

export type ReplayMapGraph = {
  nodes: ReplayMapNode[];
  edges: ReplayMapEdge[];
  width: number;
  height: number;
};

const COLUMN_WIDTH = 220;
const ROW_HEIGHT = 140;
const MARGIN_X = 100;
const MARGIN_Y = 80;

export function replayStepNodeId(step: ReplayStep): string {
  if (step.resourceType === "Page") {
    return `page:${normalizePagePath(step.resourceName || step.requestPath || "/")}`;
  }
  return `resource:${step.resourceType || "unknown"}`;
}

export function buildReplayMap(steps: readonly ReplayStep[]): ReplayMapGraph {
  const nodeMap = new Map<string, ReplayMapNode>();
  steps.forEach((step, index) => {
    const id = replayStepNodeId(step);
    const existing = nodeMap.get(id);
    if (existing) {
      existing.count += 1;
      if (step.result !== "SUCCESS") {
        existing.errorCount += 1;
      }
      return;
    }
    nodeMap.set(id, {
      id,
      label:
        step.resourceType === "Page"
          ? normalizePagePath(step.resourceName || step.requestPath || "/")
          : auditResourceLabel(step.resourceType),
      kind: step.resourceType === "Page" ? "PAGE" : "RESOURCE",
      count: 1,
      errorCount: step.result === "SUCCESS" ? 0 : 1,
      firstStepIndex: index,
      x: 0,
      y: 0,
    });
  });

  const auditNodeIds = new Map<string, string>();
  for (const step of steps) {
    if (step.auditId) {
      auditNodeIds.set(step.auditId, replayStepNodeId(step));
    }
  }

  const edgeMap = new Map<string, ReplayMapEdge>();
  steps.forEach((step, index) => {
    const current = replayStepNodeId(step);
    const parent = step.parentAuditId ? auditNodeIds.get(step.parentAuditId) : undefined;
    const previous = index > 0 ? replayStepNodeId(steps[index - 1]) : undefined;
    const from = parent ?? previous;
    if (!from || from === current) {
      return;
    }
    const key = `${from}->${current}`;
    const existing = edgeMap.get(key);
    if (existing) {
      existing.count += 1;
    } else {
      edgeMap.set(key, { from, to: current, count: 1 });
    }
  });

  const nodes = [...nodeMap.values()];
  const adjacency = new Map<string, string[]>();
  const incoming = new Map<string, number>();
  for (const node of nodes) {
    adjacency.set(node.id, []);
    incoming.set(node.id, 0);
  }
  for (const edge of edgeMap.values()) {
    adjacency.get(edge.from)?.push(edge.to);
    incoming.set(edge.to, (incoming.get(edge.to) ?? 0) + 1);
  }

  const levels = new Map<string, number>();
  const queue = nodes
    .filter((node) => (incoming.get(node.id) ?? 0) === 0)
    .sort((left, right) => left.firstStepIndex - right.firstStepIndex)
    .map((node) => node.id);
  for (const id of queue) {
    levels.set(id, 0);
  }
  while (queue.length > 0) {
    const id = queue.shift();
    if (!id) {
      continue;
    }
    const level = levels.get(id) ?? 0;
    for (const next of adjacency.get(id) ?? []) {
      if (!levels.has(next)) {
        levels.set(next, level + 1);
        queue.push(next);
      }
    }
  }
  for (const node of nodes) {
    if (!levels.has(node.id)) {
      levels.set(node.id, 0);
    }
  }

  const columns = new Map<number, ReplayMapNode[]>();
  for (const node of nodes) {
    const level = levels.get(node.id) ?? 0;
    node.x = MARGIN_X + level * COLUMN_WIDTH;
    const column = columns.get(level) ?? [];
    column.push(node);
    columns.set(level, column);
  }
  const maxRows = Math.max(...[...columns.values()].map((column) => column.length), 1);
  for (const column of columns.values()) {
    column.sort((left, right) => left.firstStepIndex - right.firstStepIndex);
    const offset = (maxRows - column.length) / 2;
    column.forEach((node, index) => {
      node.y = MARGIN_Y + (offset + index) * ROW_HEIGHT;
    });
  }

  return {
    nodes: nodes.sort((left, right) => left.firstStepIndex - right.firstStepIndex),
    edges: [...edgeMap.values()],
    width: Math.max(0, [...columns.keys()].length) * COLUMN_WIDTH + MARGIN_X * 2,
    height: maxRows * ROW_HEIGHT + MARGIN_Y * 2,
  };
}

function normalizePagePath(path: string): string {
  const segments = path.split("/").filter(Boolean);
  if (segments.length === 0) {
    return "/";
  }
  const limit =
    segments[0] === "console" ? Math.min(segments.length, 3) : Math.min(segments.length, 2);
  return `/${segments.slice(0, limit).join("/")}`;
}
