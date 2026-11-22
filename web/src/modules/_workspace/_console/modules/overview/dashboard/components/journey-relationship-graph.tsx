"use client";

import { useEffect, useMemo, useState } from "react";

import { NetworkGraph } from "@ant-design/charts";
import type { AuditJourney } from "@pudans-dev/api-client";
import { Skeleton } from "antd";

import { useMobile } from "@/hooks/ui/use-mobile";

import { formatDateTime } from "../../../../composables/_format";
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

function escapeHtml(value: string): string {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

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
    const [ready, setReady] = useState(false);
    const data = useMemo(() => buildGraphData(journey, sessions, colors), [colors, journey, sessions]);
    const nodeLabels = useMemo(() => new Map(data.nodes.map((node) => [node.id, node.data.label])), [data.nodes]);
    const pagePathCount = useMemo(
        () =>
            new Set(
                sessions.flatMap((session) =>
                    session.records.filter((record) => record.kind === "page").map((record) => pagePath(record.record)),
                ),
            ).size,
        [sessions],
    );
    const operationNodeCount = data.nodes.filter((node) => node.data.kind === "operation").length;
    const pageRecordCount = sessions.reduce((sum, session) => sum + session.pageCount, 0);
    const actionRecordCount = sessions.reduce((sum, session) => sum + session.actionCount, 0);
    const graphHeight = isMobile
        ? Math.min(620, Math.max(420, 330 + Math.sqrt(data.nodes.length) * 22))
        : Math.min(780, Math.max(560, 420 + Math.sqrt(data.nodes.length) * 24));
    const legend = [
        { color: tone("blue").base, label: "用户" },
        { color: tone("geekblue").base, label: "会话" },
        { color: tone("cyan").base, label: "入口" },
        { color: tone("green").base, label: "出口" },
        { color: tone("gold").base, label: "页面" },
        { color: tone("purple").base, label: "操作类型" },
    ];

    useEffect(() => {
        const timer = window.setTimeout(() => setReady(true), 0);

        return () => window.clearTimeout(timer);
    }, []);

    if (!ready) {
        return <Skeleton active paragraph={{ rows: 8 }} />;
    }

    return (
        <div>
            <div
                className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px]"
                style={{ color: token.colorTextSecondary }}
            >
                <span>
                    会话 <strong style={{ color: token.colorText }}>{formatCount(sessions.length)}</strong>
                </span>
                <span>
                    页面记录 <strong style={{ color: token.colorText }}>{formatCount(pageRecordCount)}</strong>
                </span>
                <span>
                    操作明细 <strong style={{ color: token.colorText }}>{formatCount(actionRecordCount)}</strong>
                </span>
                <span>
                    页面路径 <strong style={{ color: token.colorText }}>{formatCount(pagePathCount)}</strong>
                </span>
                <span>
                    操作类型 <strong style={{ color: token.colorText }}>{formatCount(operationNodeCount)}</strong>
                </span>
                <span>
                    关系 <strong style={{ color: token.colorText }}>{formatCount(data.edges.length)}</strong>
                </span>
            </div>
            <div
                className="overflow-hidden rounded-3xl border"
                style={{
                    background: token.colorFillQuaternary,
                    borderColor: token.colorBorderSecondary,
                }}
            >
                <NetworkGraph
                    autoFit="view"
                    behaviors={[
                        { key: "journey-zoom", type: "zoom-canvas" },
                        { key: "journey-drag-canvas", type: "drag-canvas" },
                        { key: "journey-drag-node", type: "drag-element" },
                        { key: "journey-label-adapt", padding: 4, type: "auto-adapt-label" },
                        { degree: 1, key: "journey-hover", type: "hover-activate" },
                    ]}
                    data={data}
                    edge={{ style: { lineWidth: 1.2, strokeOpacity: 0.5 } }}
                    height={graphHeight}
                    key={sessions.map((session) => session.index).join("-")}
                    layout={{
                        equidistant: true,
                        nodeSize: 46,
                        preventOverlap: true,
                        sortBy: (node: { data?: { level?: number } }) => node.data?.level ?? 1,
                        type: "concentric",
                    }}
                    node={{
                        style: {
                            labelBackground: true,
                            labelBackgroundFill: token.colorBgContainer,
                            labelBackgroundOpacity: 0.94,
                            labelBackgroundRadius: 4,
                            labelFill: token.colorText,
                            labelFontSize: 12,
                            labelFontWeight: 600,
                            labelLineHeight: 16,
                            labelMaxWidth: 170,
                            labelPadding: [2, 5],
                            labelPlacement: "bottom",
                            labelText: (datum) =>
                                datum.data?.labelVisible === false ? "" : String(datum.data?.label ?? datum.id),
                        },
                    }}
                    onReady={(graph) => {
                        if (!graph.destroyed) {
                            void graph.fitView({ direction: "both", when: "always" }, { duration: 240 });
                        }
                    }}
                    padding={isMobile ? 16 : 32}
                    plugins={[
                        {
                            getContent: (_event: unknown, items: unknown) => {
                                const item = (
                                    items as
                                        | {
                                              data?: { kind?: JourneyEdgeKind; label?: string };
                                              source?: string;
                                              target?: string;
                                          }[]
                                        | undefined
                                )?.[0];
                                const source = item?.source ? nodeLabels.get(item.source) : undefined;
                                const target = item?.target ? nodeLabels.get(item.target) : undefined;
                                const label =
                                    item?.data?.label ??
                                    (item?.data?.kind && source && target
                                        ? `${EDGE_KIND_LABELS[item.data.kind]}\n${source.replaceAll("\n", " ")} → ${target.replaceAll("\n", " ")}`
                                        : "");
                                const lines = escapeHtml(label).split("\n");

                                return `<div style="max-width:300px;padding:2px 2px 1px;color:${token.colorText};font-size:12px;line-height:1.6">${lines.join("<br/>")}</div>`;
                            },
                            key: "journey-tooltip",
                            style: {
                                ".tooltip": {
                                    "background-color": token.colorBgElevated,
                                    border: `1px solid ${token.colorBorderSecondary}`,
                                    "border-radius": `${token.borderRadiusLG}px`,
                                    "box-shadow": token.boxShadowSecondary,
                                    color: token.colorText,
                                    padding: "10px 12px",
                                    visibility: "hidden",
                                },
                            },
                            type: "tooltip",
                        },
                    ]}
                    zoomRange={[0.12, 1.8]}
                />
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
                <span className="sm:ml-auto">
                    全部页面路径、页面跳转和操作类型已展开；悬停查看明细，滚轮缩放，拖拽探索
                </span>
            </div>
        </div>
    );
}
