import { useMemo } from "react";

import type { ReplayStep } from "@pudans-dev/api-client";
import {
  Background,
  Controls,
  type Edge,
  Handle,
  MarkerType,
  type Node,
  type NodeProps,
  Position,
  ReactFlow,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import { cn } from "@/lib/utils";

import { buildReplayMap, replayStepNodeId } from "../lib/replay-graph";

type ReplayNodeData = {
  label: string;
  kind: "PAGE" | "RESOURCE";
  count: number;
  errorCount: number;
  firstStepIndex: number;
  active: boolean;
};

type ReplayFlowNode = Node<ReplayNodeData, "replayNode">;

const nodeTypes = { replayNode: ReplayMapNodeView };

type ReplayMapProps = {
  steps: readonly ReplayStep[];
  activeStep?: ReplayStep;
  onSelectStepIndex: (index: number) => void;
};

export function ReplayMap({ steps, activeStep, onSelectStepIndex }: ReplayMapProps) {
  const graph = useMemo(() => buildReplayMap(steps), [steps]);
  const activeNodeId = activeStep ? replayStepNodeId(activeStep) : null;

  const nodes = useMemo<ReplayFlowNode[]>(
    () =>
      graph.nodes.map((node) => ({
        id: node.id,
        type: "replayNode",
        position: { x: node.x, y: node.y },
        data: {
          label: node.label,
          kind: node.kind,
          count: node.count,
          errorCount: node.errorCount,
          firstStepIndex: node.firstStepIndex,
          active: node.id === activeNodeId,
        },
      })),
    [activeNodeId, graph.nodes],
  );

  const edges = useMemo<Edge[]>(
    () =>
      graph.edges.map((edge, index) => {
        const active = edge.from === activeNodeId || edge.to === activeNodeId;
        return {
          id: `${edge.from}-${edge.to}-${index}`,
          source: edge.from,
          target: edge.to,
          type: "smoothstep",
          animated: active,
          label: edge.count > 1 ? String(edge.count) : undefined,
          labelStyle: { fill: "var(--muted-foreground)", fontSize: 10 },
          labelBgStyle: { fill: "var(--background)" },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: active ? "var(--primary)" : "var(--muted-foreground)",
          },
          style: {
            stroke: active ? "var(--primary)" : "var(--muted-foreground)",
            strokeWidth: active ? 2 : 1.25,
            opacity: active ? 0.9 : 0.55,
          },
        };
      }),
    [activeNodeId, graph.edges],
  );

  if (graph.nodes.length === 0) {
    return null;
  }

  return (
    <div className="flex h-full flex-col gap-2">
      <div className="min-h-[460px] w-full flex-1 overflow-hidden border bg-background [&_.react-flow__controls-button]:border-border [&_.react-flow__controls-button]:bg-background [&_.react-flow__controls-button]:text-foreground [&_.react-flow__controls-button:hover]:bg-muted">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          minZoom={0.2}
          maxZoom={1.8}
          nodesConnectable={false}
          proOptions={{ hideAttribution: true }}
          onNodeClick={(_, node) => {
            const data = node.data as ReplayNodeData;
            onSelectStepIndex(data.firstStepIndex);
          }}
        >
          <Background gap={24} size={1} color="var(--border)" />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
      <p className="text-xs text-muted-foreground">
        圆圈内为步骤数，下方为异常占比；高亮节点是当前回放步骤，点击节点可跳转。
      </p>
    </div>
  );
}

function ReplayMapNodeView({ data }: NodeProps<ReplayFlowNode>) {
  const errorRate = data.count === 0 ? 0 : Math.round((data.errorCount / data.count) * 100);

  return (
    <div className="flex w-40 flex-col items-center gap-2">
      <span className="max-w-full truncate text-center text-xs font-medium" title={data.label}>
        {data.label}
      </span>
      <div
        className={cn(
          "flex size-20 flex-col items-center justify-center border-2 bg-background shadow-sm transition-shadow",
          data.active
            ? "border-primary ring-4 ring-primary/20"
            : data.errorCount > 0
              ? "border-destructive"
              : "border-primary",
        )}
      >
        <span className="text-base font-semibold tabular-nums">{data.count}</span>
        <span className="text-[10px] text-muted-foreground">{errorRate}%</span>
      </div>
      <span className="text-[10px] text-muted-foreground">
        {data.kind === "PAGE" ? "页面" : "资源"}
      </span>
      <Handle type="target" position={Position.Left} className="opacity-0" />
      <Handle type="source" position={Position.Right} className="opacity-0" />
    </div>
  );
}
