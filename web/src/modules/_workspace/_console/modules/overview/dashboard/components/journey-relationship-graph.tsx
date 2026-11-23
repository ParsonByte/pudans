"use client";

import { useMemo } from "react";

import type { AuditJourney } from "@pudans-dev/api-client";

import { EChart } from "@/components/ui";
import type { EChartsCoreOption } from "@/components/ui/echart";
import { useMobile } from "@/hooks/ui/use-mobile";

import { formatDateTime } from "../../../../composables/_format";
import { escapeHtml } from "../../../../composables/_html";
import { formatCount, operationLabel, resultTone, userLabel } from "../composables/_dashboard";
import { type JourneyTraceSession, pagePath } from "../composables/_journey";
import { useDashboardTheme } from "../composables/_theme";

type JourneyRelationshipGraphProps = {
    journey: AuditJourney;
    sessions: JourneyTraceSession[];
};

type JourneyNodeKind = "entry" | "exit" | "operation" | "page" | "session" | "user";

type JourneyEdgeKind =
    | "entry-page"
    | "page-exit"
    | "page-operation"
    | "page-transition"
    | "session-entry"
    | "session-exit"
    | "session-operation"
    | "user-session";

type JourneyGraphNode = {
    data: {
        kind: JourneyNodeKind;
        label: string;
        labelVisible?: boolean;
        level: number;
    };
    id: string;
    style: {
        fill: string;
        lineWidth: number;
        size: number;
        stroke: string;
    };
};

type JourneyGraphEdge = {
    data: {
        kind: JourneyEdgeKind;
    };
    id: string;
    source: string;
    style: {
        endArrow?: boolean;
        lineWidth: number;
        stroke: string;
        strokeOpacity: number;
    };
    target: string;
};

type JourneyGraphData = {
    edges: JourneyGraphEdge[];
    nodes: JourneyGraphNode[];
};

type GraphColors = ReturnType<typeof useDashboardTheme>;

type PageStats = {
    actions: number;
    visits: number;
};

const PAGE_LABEL_LIMIT = 16;

const EDGE_KIND_LABELS: Record<JourneyEdgeKind, string> = {
    "entry-page": "入口 · 首个页面",
    "page-exit": "页面 · 会话出口",
    "page-operation": "页面 · 操作类型",
    "page-transition": "页面跳转",
    "session-entry": "会话 · 入口",
    "session-exit": "会话 · 出口",
    "session-operation": "会话 · 操作类型",
    "user-session": "用户 · 会话",
};

function endpointLabel(kind: "entry" | "exit", path: string): string {
    return `${kind === "entry" ? "入口" : "出口"} ${path}`;
}

function buildGraphData(journey: AuditJourney, sessions: JourneyTraceSession[], colors: GraphColors): JourneyGraphData {
    const { tone } = colors;
    const nodes = new Map<string, JourneyGraphNode>();
    const edges = new Map<string, JourneyGraphEdge>();
    const pageStats = new Map<string, PageStats>();
    const operationCounts = new Map<string, number>();
    const pageOperationCounts = new Map<string, { count: number; operation: string; page: string }>();
    const transitions = new Map<string, { count: number; from: string; to: string }>();
    const addNode = (node: JourneyGraphNode) => nodes.set(node.id, node);
    const addEdge = (edge: JourneyGraphEdge) => edges.set(edge.id, edge);
    const addTransition = (from: string, to: string) => {
        if (!from || !to || from === to) {
            return;
        }

        const key = `${from}>${to}`;
        const current = transitions.get(key);
        transitions.set(key, { count: (current?.count ?? 0) + 1, from, to });
    };
    const addPageOperation = (page: string, operation: string) => {
        const key = `${page}>${operation}`;
        const current = pageOperationCounts.get(key);
        pageOperationCounts.set(key, {
            count: (current?.count ?? 0) + 1,
            operation,
            page,
        });
    };

    const identity = journey.uid ?? journey.username ?? "unknown";
    const userNode = `user:${identity}`;
    const userPalette = tone("blue");
    const status = resultTone(journey.status ?? "SUCCESS");

    addNode({
        data: {
            kind: "user",
            label: `${userLabel(journey.username ?? journey.uid)}\n${status.label} · ${formatCount(sessions.length)} 个会话`,
            level: 4,
        },
        id: userNode,
        style: { fill: userPalette.soft, lineWidth: 3, size: 58, stroke: userPalette.base },
    });

    for (const session of sessions) {
        const sessionNode = `session:${session.index}`;
        const sessionPalette = tone("geekblue");
        const entryPalette = tone("cyan");
        const exitPalette = tone("green");
        const entryNode = `entry:${session.index}`;
        const exitNode = `exit:${session.index}`;
        const sessionLabel = `会话 ${session.number} · ${formatDateTime(session.start).slice(5, 16)}`;

        addNode({
            data: {
                kind: "session",
                label: `${sessionLabel}\n${formatCount(session.pageCount)} 页 · ${formatCount(session.actionCount)} 次操作`,
                level: 3,
            },
            id: sessionNode,
            style: {
                fill: sessionPalette.soft,
                lineWidth: 2,
                size: session.pageCount === 0 ? 34 : 42,
                stroke: sessionPalette.base,
            },
        });
        addNode({
            data: {
                kind: "entry",
                label: endpointLabel("entry", session.entryPath),
                level: 2,
            },
            id: entryNode,
            style: { fill: entryPalette.soft, lineWidth: 1.5, size: 32, stroke: entryPalette.base },
        });
        addNode({
            data: {
                kind: "exit",
                label: endpointLabel("exit", session.exitPath),
                level: 2,
            },
            id: exitNode,
            style: { fill: exitPalette.soft, lineWidth: 1.5, size: 32, stroke: exitPalette.base },
        });
        addEdge({
            data: { kind: "user-session" },
            id: `${userNode}>${sessionNode}`,
            source: userNode,
            style: { lineWidth: 1.8, stroke: sessionPalette.base, strokeOpacity: 0.62 },
            target: sessionNode,
        });
        addEdge({
            data: { kind: "session-entry" },
            id: `${sessionNode}>${entryNode}`,
            source: sessionNode,
            style: { lineWidth: 1.3, stroke: entryPalette.base, strokeOpacity: 0.5 },
            target: entryNode,
        });
        addEdge({
            data: { kind: "session-exit" },
            id: `${sessionNode}>${exitNode}`,
            source: sessionNode,
            style: { lineWidth: 1.3, stroke: exitPalette.base, strokeOpacity: 0.5 },
            target: exitNode,
        });

        let currentPage: string | undefined;
        let firstPage: string | undefined;
        let lastPage: string | undefined;
        let previousPage: string | undefined;
        for (const item of session.records) {
            if (item.kind === "page") {
                const path = pagePath(item.record);
                const stats = pageStats.get(path) ?? { actions: 0, visits: 0 };
                pageStats.set(path, { ...stats, visits: stats.visits + 1 });
                firstPage ??= path;
                lastPage = path;
                addTransition(previousPage ?? "", path);
                previousPage = path;
                currentPage = path;
                continue;
            }

            const operation = item.record.operation;
            if (!operation) {
                continue;
            }

            operationCounts.set(operation, (operationCounts.get(operation) ?? 0) + 1);
            if (currentPage) {
                const stats = pageStats.get(currentPage) ?? { actions: 0, visits: 0 };
                pageStats.set(currentPage, { ...stats, actions: stats.actions + 1 });
                addPageOperation(currentPage, operation);
                continue;
            }

            const operationNode = `operation:${operation}`;
            const operationPalette = tone("purple");
            addEdge({
                data: { kind: "session-operation" },
                id: `${sessionNode}>${operationNode}`,
                source: sessionNode,
                style: { lineWidth: 1, stroke: operationPalette.base, strokeOpacity: 0.42 },
                target: operationNode,
            });
        }

        if (firstPage) {
            addEdge({
                data: { kind: "entry-page" },
                id: `${entryNode}>page:${firstPage}`,
                source: entryNode,
                style: {
                    endArrow: true,
                    lineWidth: 1.5,
                    stroke: entryPalette.base,
                    strokeOpacity: 0.6,
                },
                target: `page:${firstPage}`,
            });
        }

        if (lastPage) {
            addEdge({
                data: { kind: "page-exit" },
                id: `page:${lastPage}>${exitNode}`,
                source: `page:${lastPage}`,
                style: {
                    endArrow: true,
                    lineWidth: 1.5,
                    stroke: exitPalette.base,
                    strokeOpacity: 0.6,
                },
                target: exitNode,
            });
        }
    }

    const pagePalette = tone("gold");
    const labeledPages = new Set(
        Array.from(pageStats.entries())
            .sort((left, right) => right[1].visits - left[1].visits)
            .slice(0, PAGE_LABEL_LIMIT)
            .map(([path]) => path),
    );

    for (const [path, stats] of pageStats) {
        addNode({
            data: {
                kind: "page",
                label: `${path}\n${formatCount(stats.visits)} 次访问 · ${formatCount(stats.actions)} 次操作`,
                labelVisible: labeledPages.has(path),
                level: 2,
            },
            id: `page:${path}`,
            style: {
                fill: pagePalette.soft,
                lineWidth: 1.8,
                size: Math.min(48, 24 + Math.log2(stats.visits + 1) * 4),
                stroke: pagePalette.base,
            },
        });
    }

    for (const operation of journey.operations ?? []) {
        operationCounts.set(operation, operationCounts.get(operation) ?? 0);
    }

    const operationPalette = tone("purple");
    for (const [operation, count] of operationCounts) {
        addNode({
            data: {
                kind: "operation",
                label: `操作 · ${operationLabel(operation)}\n${formatCount(count)} 次`,
                level: 1,
            },
            id: `operation:${operation}`,
            style: {
                fill: operationPalette.soft,
                lineWidth: 1.5,
                size: Math.min(42, 24 + Math.log2(count + 1) * 3),
                stroke: operationPalette.base,
            },
        });
    }

    for (const item of pageOperationCounts.values()) {
        addEdge({
            data: { kind: "page-operation" },
            id: `page:${item.page}>operation:${item.operation}`,
            source: `page:${item.page}`,
            style: {
                lineWidth: Math.min(3.5, 1 + Math.log2(item.count + 1)),
                stroke: operationPalette.base,
                strokeOpacity: 0.32,
            },
            target: `operation:${item.operation}`,
        });
    }

    const transitionPalette = tone("orange");
    for (const item of transitions.values()) {
        addEdge({
            data: { kind: "page-transition" },
            id: `page:${item.from}>page:${item.to}`,
            source: `page:${item.from}`,
            style: {
                endArrow: true,
                lineWidth: Math.min(4, 1.2 + Math.log2(item.count + 1)),
                stroke: transitionPalette.base,
                strokeOpacity: 0.52,
            },
            target: `page:${item.to}`,
        });
    }

    return { edges: Array.from(edges.values()), nodes: Array.from(nodes.values()) };
}

export function JourneyRelationshipGraph({ journey, sessions }: JourneyRelationshipGraphProps) {
    const isMobile = useMobile();
    const colors = useDashboardTheme();
    const { token, tone } = colors;
    const data = useMemo(() => buildGraphData(journey, sessions, colors), [colors, journey, sessions]);
    const nodeLabels = useMemo(() => new Map(data.nodes.map((node) => [node.id, node.data.label])), [data.nodes]);
    const graphHeight = isMobile
        ? Math.min(620, Math.max(420, 330 + Math.sqrt(data.nodes.length) * 22))
        : Math.min(780, Math.max(560, 420 + Math.sqrt(data.nodes.length) * 24));
    const legend = useMemo(
        () => [
            { color: tone("blue").base, label: "用户" },
            { color: tone("geekblue").base, label: "会话" },
            { color: tone("cyan").base, label: "入口" },
            { color: tone("green").base, label: "出口" },
            { color: tone("gold").base, label: "页面" },
            { color: tone("purple").base, label: "操作类型" },
        ],
        [tone],
    );
    const option = useMemo<EChartsCoreOption>(() => {
        const categoryIndex: Record<JourneyNodeKind, number> = {
            entry: 2,
            exit: 3,
            operation: 5,
            page: 4,
            session: 1,
            user: 0,
        };
        const nodes = data.nodes.map((node) => ({
            category: categoryIndex[node.data.kind],
            dataLabel: node.data.label,
            id: node.id,
            itemStyle: {
                borderColor: node.style.stroke,
                borderWidth: node.style.lineWidth,
                color: node.style.fill,
            },
            label: {
                show: node.data.kind !== "page" || node.data.labelVisible !== false,
            },
            name: node.id,
            symbolSize: node.style.size,
        }));
        const links = data.edges.map((edge) => {
            const sourceLabel = nodeLabels.get(edge.source)?.replaceAll("\n", " ") ?? edge.source;
            const targetLabel = nodeLabels.get(edge.target)?.replaceAll("\n", " ") ?? edge.target;

            return {
                data: {
                    kind: edge.data.kind,
                    label: `${EDGE_KIND_LABELS[edge.data.kind]}\n${sourceLabel} → ${targetLabel}`,
                },
                lineStyle: {
                    color: edge.style.stroke,
                    curveness: 0.08,
                    opacity: edge.style.strokeOpacity,
                    width: edge.style.lineWidth,
                },
                source: edge.source,
                target: edge.target,
            };
        });

        return {
            animationDuration: 420,
            series: [
                {
                    blur: {
                        itemStyle: { opacity: 0.28 },
                        label: { opacity: 0.28 },
                        lineStyle: { opacity: 0.12 },
                    },
                    bottom: 12,
                    categories: legend.map((item) => ({ itemStyle: { color: item.color }, name: item.label })),
                    data: nodes,
                    draggable: true,
                    edgeSymbol: ["none", "arrow"],
                    edgeSymbolSize: 6,
                    emphasis: {
                        focus: "adjacency",
                        label: { show: true },
                        lineStyle: { opacity: 0.9, width: 2.2 },
                    },
                    force: {
                        edgeLength: [48, 180],
                        gravity: 0.08,
                        initLayout: "circular",
                        repulsion: 260,
                    },
                    label: {
                        color: token.colorText,
                        distance: 4,
                        fontSize: 11,
                        formatter: (params: { data?: { dataLabel?: string } }) => params.data?.dataLabel ?? "",
                        lineHeight: 15,
                        overflow: "break",
                        position: "bottom",
                        width: 170,
                    },
                    layout: "force",
                    left: 12,
                    lineStyle: { opacity: 0.45 },
                    links,
                    right: 12,
                    roam: true,
                    top: 12,
                    type: "graph",
                },
            ],
            tooltip: {
                backgroundColor: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
                borderWidth: 1,
                confine: true,
                extraCssText: `border-radius:${token.borderRadiusLG};box-shadow:${token.boxShadowSecondary};`,
                formatter: (raw: unknown) => {
                    const params = raw as {
                        data?: { dataLabel?: string; label?: string };
                        dataType?: "edge" | "node";
                    };
                    const label =
                        params.dataType === "edge" ? (params.data?.label ?? "") : (params.data?.dataLabel ?? "");

                    return `<div style="max-width:320px;white-space:normal;color:${token.colorText};font-size:12px;line-height:1.65">${escapeHtml(label).replaceAll("\n", "<br/>")}</div>`;
                },
                textStyle: {
                    color: token.colorText,
                    fontSize: 12,
                },
                trigger: "item",
            },
        };
    }, [data, legend, nodeLabels, token]);

    return (
        <div>
            <div
                className="overflow-hidden rounded-3xl border"
                style={{
                    background: token.colorFillQuaternary,
                    borderColor: token.colorBorderSecondary,
                }}
            >
                <EChart height={graphHeight} key={sessions.map((session) => session.index).join("-")} option={option} />
            </div>
            <div
                className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px]"
                style={{ color: token.colorTextTertiary }}
            >
                {legend.map((item) => (
                    <span className="flex items-center gap-1.5" key={item.label}>
                        <span className="h-2 w-2 rounded-full" style={{ background: item.color }} />
                        {item.label}
                    </span>
                ))}
            </div>
        </div>
    );
}
