import { useMemo } from "react";

import type { ReplayStep } from "@pudans-dev/api-client";

import {
  SigmaGraph,
  type SigmaGraphEdge,
  type SigmaGraphNode,
} from "@/components/graph/sigma-graph";

import { buildReplayMap, replayStepNodeId } from "../lib/replay-graph";

type ReplayMapProps = {
  steps: readonly ReplayStep[];
  activeStep?: ReplayStep;
  onSelectStepIndex: (index: number) => void;
};

export function ReplayMap({ steps, activeStep, onSelectStepIndex }: ReplayMapProps) {
  const graph = useMemo(() => buildReplayMap(steps), [steps]);
  const activeNodeId = activeStep ? replayStepNodeId(activeStep) : null;

  const nodes = useMemo<SigmaGraphNode[]>(
    () =>
      graph.nodes.map((node) => ({
        id: node.id,
        label: `${node.label} · ${node.count}`,
        x: node.x,
        y: node.y,
        size: 10 + Math.min(14, Math.log2(node.count + 1) * 5),
        tone:
          node.id === activeNodeId ? "primary" : node.errorCount > 0 ? "destructive" : undefined,
      })),
    [activeNodeId, graph.nodes],
  );

  const edges = useMemo<SigmaGraphEdge[]>(
    () =>
      graph.edges.map((edge) => {
        const active = edge.from === activeNodeId || edge.to === activeNodeId;
        return {
          source: edge.from,
          target: edge.to,
          label: edge.count > 1 ? String(edge.count) : undefined,
          tone: active ? "primary" : "muted",
          size: active ? 2 : 1.2,
        };
      }),
    [activeNodeId, graph.edges],
  );

  if (graph.nodes.length === 0) {
    return null;
  }

  return (
    <div className="flex h-full flex-col gap-2">
      <SigmaGraph
        nodes={nodes}
        edges={edges}
        height={460}
        layout="preset"
        colorByCommunity
        ariaLabel="回放导航图"
        className="min-h-[460px] flex-1"
        onNodeClick={(nodeId) => {
          const node = graph.nodes.find((item) => item.id === nodeId);
          if (node) {
            onSelectStepIndex(node.firstStepIndex);
          }
        }}
      />
      <p className="text-xs text-muted-foreground">
        节点为页面或资源，标签后为步骤数；高亮节点是当前回放步骤，点击节点可跳转。
      </p>
    </div>
  );
}
