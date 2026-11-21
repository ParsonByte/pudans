"use client";

import { useEffect, useMemo } from "react";

import {
  ControlsContainer,
  SigmaContainer,
  ZoomControl,
  useLoadGraph,
  useRegisterEvents,
} from "@react-sigma/core";
import "@react-sigma/core/lib/style.css";

import { useChartTokens } from "@/components/charts/chart-theme";

import { type SigmaGraphEdge, type SigmaGraphNode, buildSigmaGraph } from "./graph-model";

type SigmaCanvasProps = {
  nodes: SigmaGraphNode[];
  edges: SigmaGraphEdge[];
  layout: "force" | "preset";
  colorByCommunity: boolean;
  onNodeClick?: (nodeId: string) => void;
};

function GraphController({
  nodes,
  edges,
  layout,
  colorByCommunity,
  onNodeClick,
}: SigmaCanvasProps) {
  const tokens = useChartTokens();
  const loadGraph = useLoadGraph();
  const registerEvents = useRegisterEvents();
  const graph = useMemo(
    () => buildSigmaGraph({ nodes, edges, tokens, layout, colorByCommunity }),
    [colorByCommunity, edges, layout, nodes, tokens],
  );

  useEffect(() => {
    loadGraph(graph, true);
  }, [graph, loadGraph]);

  useEffect(() => {
    if (!onNodeClick) {
      return;
    }
    registerEvents({
      clickNode: ({ node }) => onNodeClick(node),
    });
  }, [onNodeClick, registerEvents]);

  return null;
}

export function SigmaCanvas(props: SigmaCanvasProps) {
  return (
    <SigmaContainer
      style={{ height: "100%", width: "100%" }}
      options={{
        settings: {
          renderEdgeLabels: true,
          labelDensity: 0.7,
          labelGridCellSize: 100,
          labelRenderedSizeThreshold: 6,
          enableEdgeEvents: false,
          hideEdgesOnMove: true,
          hideLabelsOnMove: false,
          autoRescale: true,
          minCameraRatio: 0.2,
          maxCameraRatio: 3,
          enableCameraRotation: false,
        },
      }}
    >
      <GraphController {...props} />
      <ControlsContainer position="bottom-right">
        <ZoomControl labels={{ zoomIn: "放大", zoomOut: "缩小", reset: "重置" }} />
      </ControlsContainer>
    </SigmaContainer>
  );
}
