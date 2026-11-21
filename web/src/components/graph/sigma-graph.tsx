"use client";

import dynamic from "next/dynamic";

import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

import type { SigmaGraphEdge, SigmaGraphNode } from "./graph-model";

const SigmaCanvas = dynamic(() => import("./sigma-canvas").then((module) => module.SigmaCanvas), {
  ssr: false,
  loading: () => <Skeleton className="h-full w-full" />,
});

type SigmaGraphProps = {
  nodes: SigmaGraphNode[];
  edges: SigmaGraphEdge[];
  height?: number | string;
  className?: string;
  ariaLabel?: string;
  layout?: "force" | "preset";
  colorByCommunity?: boolean;
  onNodeClick?: (nodeId: string) => void;
};

export function SigmaGraph({
  nodes,
  edges,
  height = 420,
  className,
  ariaLabel,
  layout = "force",
  colorByCommunity = false,
  onNodeClick,
}: SigmaGraphProps) {
  return (
    <div
      data-motion="enter"
      role="img"
      aria-label={ariaLabel}
      className={cn("w-full overflow-hidden border bg-background", className)}
      style={{ height }}
    >
      <SigmaCanvas
        nodes={nodes}
        edges={edges}
        layout={layout}
        colorByCommunity={colorByCommunity}
        onNodeClick={onNodeClick}
      />
    </div>
  );
}

export type { SigmaGraphEdge, SigmaGraphNode } from "./graph-model";
