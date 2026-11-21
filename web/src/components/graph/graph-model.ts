import { MultiDirectedGraph, UndirectedGraph } from "graphology";
import louvain from "graphology-communities-louvain";
import forceAtlas2 from "graphology-layout-forceatlas2";

import type { ChartTokens } from "@/components/charts/chart-theme";

type GraphTone =
  "primary" | "destructive" | "muted" | "chart1" | "chart2" | "chart3" | "chart4" | "chart5";

export type SigmaGraphNode = {
  id: string;
  label: string;
  tone?: GraphTone;
  size?: number;
  x?: number;
  y?: number;
};

export type SigmaGraphEdge = {
  source: string;
  target: string;
  label?: string;
  tone?: GraphTone;
  size?: number;
};

type BuildGraphOptions = {
  nodes: SigmaGraphNode[];
  edges: SigmaGraphEdge[];
  tokens: ChartTokens;
  layout: "force" | "preset";
  colorByCommunity: boolean;
};

const TONE_TOKENS: Record<GraphTone, keyof ChartTokens> = {
  primary: "primary",
  destructive: "destructive",
  muted: "mutedForeground",
  chart1: "chart1",
  chart2: "chart2",
  chart3: "chart3",
  chart4: "chart4",
  chart5: "chart5",
};

const COMMUNITY_TONES: GraphTone[] = ["chart2", "chart3", "chart4", "chart1", "chart5", "primary"];

const SEED_RADIUS = 120;

const FORCE_ITERATIONS = 220;

function toneColor(tone: GraphTone | undefined, tokens: ChartTokens): string {
  if (!tone) {
    return tokens.chart2;
  }
  return tokens[TONE_TOKENS[tone]];
}

function seedPosition(index: number, total: number): { x: number; y: number } {
  const angle = (index / Math.max(total, 1)) * Math.PI * 2;
  return {
    x: Math.cos(angle) * SEED_RADIUS,
    y: Math.sin(angle) * SEED_RADIUS,
  };
}

function assignCommunities(
  graph: MultiDirectedGraph,
  nodes: SigmaGraphNode[],
  tokens: ChartTokens,
): void {
  if (nodes.length === 0) {
    return;
  }
  const communityGraph = new UndirectedGraph();
  for (const node of nodes) {
    communityGraph.addNode(node.id);
  }
  graph.forEachEdge((_edge, _attributes, source, target) => {
    if (!communityGraph.hasNode(source) || !communityGraph.hasNode(target)) {
      return;
    }
    if (source === target || communityGraph.hasEdge(source, target)) {
      return;
    }
    communityGraph.addEdge(source, target);
  });
  if (communityGraph.size === 0) {
    return;
  }
  louvain.assign(communityGraph, { nodeCommunityAttribute: "community" });
  communityGraph.forEachNode((id, attributes) => {
    const community = Number(attributes.community ?? 0);
    graph.setNodeAttribute(
      id,
      "color",
      toneColor(COMMUNITY_TONES[community % COMMUNITY_TONES.length], tokens),
    );
  });
}

export function buildSigmaGraph({
  nodes,
  edges,
  tokens,
  layout,
  colorByCommunity,
}: BuildGraphOptions): MultiDirectedGraph {
  const graph = new MultiDirectedGraph();

  nodes.forEach((node, index) => {
    const seed = seedPosition(index, nodes.length);
    graph.addNode(node.id, {
      label: node.label,
      x: node.x ?? seed.x,
      y: node.y ?? seed.y,
      size: node.size ?? 10,
      color: toneColor(node.tone, tokens),
      labelColor: tokens.foreground,
    });
  });

  for (const edge of edges) {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) {
      continue;
    }
    graph.addEdge(edge.source, edge.target, {
      label: edge.label,
      size: edge.size ?? 1.2,
      color: toneColor(edge.tone ?? "muted", tokens),
      labelColor: tokens.mutedForeground,
    });
  }

  if (colorByCommunity) {
    assignCommunities(graph, nodes, tokens);
  }

  if (layout === "force" && graph.order > 1) {
    forceAtlas2.assign(graph, {
      iterations: FORCE_ITERATIONS,
      settings: {
        ...forceAtlas2.inferSettings(graph),
        linLogMode: true,
        adjustSizes: true,
        scalingRatio: 12,
        gravity: 1,
      },
    });
  }

  return graph;
}
