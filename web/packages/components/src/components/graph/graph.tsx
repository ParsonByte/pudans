import {
  forwardRef,
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type HTMLAttributes,
  type PointerEvent as ReactPointerEvent,
  type WheelEvent as ReactWheelEvent,
} from "react";
import { cn } from "../../utils/cn";
import styles from "./graph.module.css";

export type GraphNodeTone = "neutral" | "brand" | "success" | "warning" | "danger";

export interface GraphNode {
  id: string;
  label: string;
  description?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  tone?: GraphNodeTone;
}

export interface GraphEdge {
  id?: string;
  source: string;
  target: string;
  label?: string;
  dashed?: boolean;
  color?: string;
}

export interface GraphProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  nodes: readonly GraphNode[];
  edges: readonly GraphEdge[];
  height?: number;
  nodeWidth?: number;
  nodeHeight?: number;
  draggable?: boolean;
  fitPadding?: number;
  selectedNodeId?: string;
  onNodeClick?: (node: GraphNode) => void;
  onNodeMove?: (nodeId: string, position: { x: number; y: number }) => void;
  ariaLabel?: string;
}

interface Position {
  x: number;
  y: number;
}

interface Viewport {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface EdgeGeometry {
  key: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  label?: string;
  color: string;
  dashed: boolean;
}

const DEFAULT_VIEW: Viewport = { x: -420, y: -300, width: 840, height: 600 };
const MIN_VIEW_WIDTH = 180;
const MAX_VIEW_WIDTH = 5000;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function nodeSize(
  node: GraphNode,
  fallbackWidth: number,
  fallbackHeight: number,
): { width: number; height: number } {
  return {
    width: node.width ?? fallbackWidth,
    height: node.height ?? fallbackHeight,
  };
}

function initialPositions(nodes: readonly GraphNode[]): Record<string, Position> {
  const radius = Math.max(180, nodes.length * 34);
  const positions: Record<string, Position> = {};

  nodes.forEach((node, index) => {
    if (node.x !== undefined && node.y !== undefined) {
      positions[node.id] = { x: node.x, y: node.y };
      return;
    }
    if (nodes.length === 1) {
      positions[node.id] = { x: 0, y: 0 };
      return;
    }
    const angle = (index / nodes.length) * Math.PI * 2 - Math.PI / 2;
    positions[node.id] = {
      x: Math.cos(angle) * radius,
      y: Math.sin(angle) * radius,
    };
  });

  return positions;
}

function fitViewport(
  nodes: readonly GraphNode[],
  positions: Record<string, Position>,
  fallbackWidth: number,
  fallbackHeight: number,
  padding: number,
): Viewport {
  if (nodes.length === 0) {
    return DEFAULT_VIEW;
  }

  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;

  for (const node of nodes) {
    const position = positions[node.id];
    if (!position) {
      continue;
    }
    const size = nodeSize(node, fallbackWidth, fallbackHeight);
    minX = Math.min(minX, position.x - size.width / 2);
    minY = Math.min(minY, position.y - size.height / 2);
    maxX = Math.max(maxX, position.x + size.width / 2);
    maxY = Math.max(maxY, position.y + size.height / 2);
  }

  if (!Number.isFinite(minX) || !Number.isFinite(minY)) {
    return DEFAULT_VIEW;
  }

  return {
    x: minX - padding,
    y: minY - padding,
    width: Math.max(240, maxX - minX + padding * 2),
    height: Math.max(180, maxY - minY + padding * 2),
  };
}

function boundaryPoint(
  from: Position,
  to: Position,
  node: GraphNode,
  fallbackWidth: number,
  fallbackHeight: number,
): Position {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const distance = Math.hypot(dx, dy);
  if (distance === 0) {
    return from;
  }
  const size = nodeSize(node, fallbackWidth, fallbackHeight);
  const scale =
    1 / Math.max(Math.abs(dx) / (size.width / 2 + 6), Math.abs(dy) / (size.height / 2 + 6));
  return {
    x: from.x + dx * scale,
    y: from.y + dy * scale,
  };
}

function truncateLabel(label: string, maxLength: number): string {
  return label.length > maxLength ? `${label.slice(0, maxLength - 1)}…` : label;
}

/**
 * Lightweight SVG relationship graph: node dragging, wheel zoom, background
 * panning, fit-to-bounds and arrowheads. No graph library is required.
 */
export const Graph = forwardRef<HTMLDivElement, GraphProps>(function Graph(
  {
    nodes,
    edges,
    height = 480,
    nodeWidth = 156,
    nodeHeight = 64,
    draggable = true,
    fitPadding = 80,
    selectedNodeId,
    onNodeClick,
    onNodeMove,
    ariaLabel = "Relationship graph",
    className,
    style,
    ...props
  },
  ref,
) {
  const nodeIds = nodes.map((node) => node.id).join("|");
  const initial = useMemo(
    () => initialPositions(nodes),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [nodeIds],
  );
  const [positions, setPositions] = useState<Record<string, Position>>(initial);
  const [view, setView] = useState<Viewport>(() =>
    fitViewport(nodes, initial, nodeWidth, nodeHeight, fitPadding),
  );
  const svgRef = useRef<SVGSVGElement>(null);
  const dragRef = useRef<{
    id: string;
    offsetX: number;
    offsetY: number;
    moved: boolean;
  } | null>(null);
  const panRef = useRef<{
    startX: number;
    startY: number;
    view: Viewport;
  } | null>(null);
  const justDraggedRef = useRef(false);
  const markerId = `pd-graph-${useId().replace(/:/g, "")}`;

  useEffect(() => {
    const layout = initialPositions(nodes);
    setPositions(layout);
    setView(fitViewport(nodes, layout, nodeWidth, nodeHeight, fitPadding));
    // Only refit when the node identity set changes; dragging keeps local positions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodeIds]);

  const nodeMap = useMemo(() => new Map(nodes.map((node) => [node.id, node])), [nodes]);
  const colorFor = (index: number): string => `rgb(var(--pd-chart-${(index % 6) + 1}))`;

  const screenToWorld = useCallback((clientX: number, clientY: number): Position | null => {
    const svg = svgRef.current;
    if (!svg) {
      return null;
    }
    const point = svg.createSVGPoint();
    point.x = clientX;
    point.y = clientY;
    const matrix = svg.getScreenCTM();
    if (!matrix) {
      return null;
    }
    const world = point.matrixTransform(matrix.inverse());
    return { x: world.x, y: world.y };
  }, []);

  const fit = useCallback(() => {
    setView(fitViewport(nodes, positions, nodeWidth, nodeHeight, fitPadding));
  }, [fitPadding, nodeHeight, nodeWidth, nodes, positions]);

  const zoomBy = useCallback((factor: number) => {
    setView((current) => {
      const width = clamp(current.width * factor, MIN_VIEW_WIDTH, MAX_VIEW_WIDTH);
      const ratio = width / current.width;
      const centerX = current.x + current.width / 2;
      const centerY = current.y + current.height / 2;
      const height = current.height * ratio;
      return {
        x: centerX - width / 2,
        y: centerY - height / 2,
        width,
        height,
      };
    });
  }, []);

  const handleWheel = (event: ReactWheelEvent<SVGSVGElement>): void => {
    event.preventDefault();
    const world = screenToWorld(event.clientX, event.clientY);
    if (!world) {
      return;
    }
    const factor = event.deltaY > 0 ? 1.12 : 0.89;
    setView((current) => {
      const width = clamp(current.width * factor, MIN_VIEW_WIDTH, MAX_VIEW_WIDTH);
      const ratio = width / current.width;
      return {
        x: world.x - (world.x - current.x) * ratio,
        y: world.y - (world.y - current.y) * ratio,
        width,
        height: current.height * ratio,
      };
    });
  };

  const handleBackgroundPointerDown = (event: ReactPointerEvent<SVGSVGElement>): void => {
    if (event.button !== 0) {
      return;
    }
    panRef.current = {
      startX: event.clientX,
      startY: event.clientY,
      view,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleNodePointerDown = (event: ReactPointerEvent<SVGGElement>, node: GraphNode): void => {
    if (!draggable || event.button !== 0) {
      return;
    }
    event.stopPropagation();
    const current = positions[node.id];
    const world = screenToWorld(event.clientX, event.clientY);
    if (!current || !world) {
      return;
    }
    dragRef.current = {
      id: node.id,
      offsetX: world.x - current.x,
      offsetY: world.y - current.y,
      moved: false,
    };
    justDraggedRef.current = false;
    svgRef.current?.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: ReactPointerEvent<SVGSVGElement>): void => {
    if (dragRef.current) {
      const world = screenToWorld(event.clientX, event.clientY);
      if (!world) {
        return;
      }
      dragRef.current.moved = true;
      justDraggedRef.current = true;
      const next = {
        x: world.x - dragRef.current.offsetX,
        y: world.y - dragRef.current.offsetY,
      };
      setPositions((current) => ({
        ...current,
        [dragRef.current!.id]: next,
      }));
      onNodeMove?.(dragRef.current.id, next);
      return;
    }

    if (panRef.current) {
      const rect = svgRef.current?.getBoundingClientRect();
      if (!rect) {
        return;
      }
      const scaleX = panRef.current.view.width / rect.width;
      const scaleY = panRef.current.view.height / rect.height;
      setView({
        ...panRef.current.view,
        x: panRef.current.view.x - (event.clientX - panRef.current.startX) * scaleX,
        y: panRef.current.view.y - (event.clientY - panRef.current.startY) * scaleY,
      });
    }
  };

  const handlePointerUp = (): void => {
    panRef.current = null;
    dragRef.current = null;
  };

  const edgeGeometry = useMemo<EdgeGeometry[]>(() => {
    return edges.flatMap((edge, index) => {
      const source = nodeMap.get(edge.source);
      const target = nodeMap.get(edge.target);
      const sourcePosition = positions[edge.source];
      const targetPosition = positions[edge.target];
      if (!source || !target || !sourcePosition || !targetPosition) {
        return [];
      }
      const start = boundaryPoint(sourcePosition, targetPosition, source, nodeWidth, nodeHeight);
      const end = boundaryPoint(targetPosition, sourcePosition, target, nodeWidth, nodeHeight);
      return [
        {
          key: edge.id ?? `${edge.source}-${edge.target}-${index}`,
          x1: start.x,
          y1: start.y,
          x2: end.x,
          y2: end.y,
          label: edge.label,
          color: edge.color ?? colorFor(index),
          dashed: edge.dashed ?? false,
        },
      ];
    });
  }, [edges, nodeHeight, nodeMap, nodeWidth, positions]);

  const hasNodes = nodes.length > 0;

  return (
    <div
      ref={ref}
      className={cn(styles.root, className)}
      style={{ height, ...style } as CSSProperties}
      {...props}
    >
      {!hasNodes ? (
        <div className={styles.empty}>No graph data</div>
      ) : (
        <>
          <svg
            ref={svgRef}
            className={styles.canvas}
            viewBox={`${view.x} ${view.y} ${view.width} ${view.height}`}
            preserveAspectRatio="xMidYMid meet"
            role="application"
            aria-label={ariaLabel}
            onWheel={handleWheel}
            onPointerDown={handleBackgroundPointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
          >
            <defs>
              {edgeGeometry.map((edge, index) => (
                <marker
                  key={edge.key}
                  id={`${markerId}-${index}`}
                  markerWidth="8"
                  markerHeight="8"
                  refX="7"
                  refY="4"
                  orient="auto"
                  markerUnits="strokeWidth"
                >
                  <path d="M0,0 L8,4 L0,8 Z" fill={edge.color} />
                </marker>
              ))}
            </defs>

            <g className={styles.edges}>
              {edgeGeometry.map((edge, index) => (
                <g key={edge.key}>
                  <line
                    className={cn(styles.edge, edge.dashed && styles.dashed)}
                    x1={edge.x1}
                    y1={edge.y1}
                    x2={edge.x2}
                    y2={edge.y2}
                    stroke={edge.color}
                    markerEnd={`url(#${markerId}-${index})`}
                  />
                  {edge.label ? (
                    <text
                      className={styles.edgeLabel}
                      x={(edge.x1 + edge.x2) / 2}
                      y={(edge.y1 + edge.y2) / 2 - 6}
                      textAnchor="middle"
                    >
                      {edge.label}
                    </text>
                  ) : null}
                </g>
              ))}
            </g>

            <g className={styles.nodes}>
              {nodes.map((node) => {
                const position = positions[node.id];
                if (!position) {
                  return null;
                }
                const size = nodeSize(node, nodeWidth, nodeHeight);
                const selected = selectedNodeId === node.id;
                return (
                  <g
                    key={node.id}
                    className={cn(
                      styles.nodeGroup,
                      draggable && styles.draggable,
                      node.tone && styles[node.tone],
                      selected && styles.selected,
                    )}
                    transform={`translate(${position.x} ${position.y})`}
                    role="button"
                    tabIndex={0}
                    aria-label={node.label}
                    onPointerDown={(event) => handleNodePointerDown(event, node)}
                    onClick={() => {
                      if (justDraggedRef.current) {
                        justDraggedRef.current = false;
                        return;
                      }
                      onNodeClick?.(node);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onNodeClick?.(node);
                      }
                    }}
                  >
                    <rect
                      className={styles.node}
                      x={-size.width / 2}
                      y={-size.height / 2}
                      width={size.width}
                      height={size.height}
                      rx={12}
                      ry={12}
                    />
                    <text
                      className={styles.nodeLabel}
                      textAnchor="middle"
                      y={node.description ? -3 : 5}
                    >
                      {truncateLabel(node.label, 20)}
                    </text>
                    {node.description ? (
                      <text className={styles.nodeDescription} textAnchor="middle" y={15}>
                        {truncateLabel(node.description, 24)}
                      </text>
                    ) : null}
                  </g>
                );
              })}
            </g>
          </svg>

          <div className={styles.toolbar} role="group" aria-label="Graph controls">
            <button type="button" onClick={() => zoomBy(0.85)} aria-label="Zoom in">
              +
            </button>
            <button type="button" onClick={() => zoomBy(1.18)} aria-label="Zoom out">
              −
            </button>
            <button type="button" onClick={fit} aria-label="Fit graph">
              Fit
            </button>
          </div>
        </>
      )}
    </div>
  );
});

Graph.displayName = "Graph";
