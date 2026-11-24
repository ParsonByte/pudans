"use client";

import { useMemo } from "react";

import type { AuditSessionSummary } from "@pudans-dev/api-client";

import { EChart } from "@/components/ui";
import type { EChartsCoreOption } from "@/components/ui/echart";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useResizeObserver } from "@/hooks/utils/use-resize-observer";

import { formatCount, formatDateTime } from "../../../../composables/_format";
import { escapeHtml } from "../../../../composables/_html";
import { formatJourneyDuration, userLabel } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

type JourneyRelationshipGraphProps = {
    sessions: AuditSessionSummary[];
};

type JourneyNodeKind = "page" | "session" | "user";

type JourneyGraphNode = {
    detail: string;
    fill: string;
    id: string;
    kind: JourneyNodeKind;
    size: number;
    stroke: string;
    title: string;
    x: number;
    y: number;
};

type JourneyGraphEdge = {
    color: string;
    count: number;
    id: string;
    kind: "session-page" | "user-session";
    source: string;
    target: string;
};

type UserStat = {
    actionCount: number;
    latest: number;
    pageCount: number;
    sessionCount: number;
    username?: string;
};

type PageStat = {
    entryCount: number;
    exitCount: number;
    firstOrder: number;
    path: string;
};

const NODE_KIND_LABELS: Record<JourneyNodeKind, string> = {
    page: "访问页面",
    session: "用户会话",
    user: "用户",
};

const NODE_ICONS: Record<JourneyNodeKind, string> = {
    page: '<path d="M27 21h13l7 7v22H27V21Z" fill="none" stroke="currentColor" stroke-linejoin="round" stroke-width="3.5"/><path d="M40 21v7h7M33 35h8M33 41h8" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="3"/>',
    session:
        '<path d="M22 26h28a4 4 0 0 1 4 4v13a4 4 0 0 1-4 4H38l-9 7v-7h-7a4 4 0 0 1-4-4V30a4 4 0 0 1 4-4Z" fill="none" stroke="currentColor" stroke-linejoin="round" stroke-width="3.5"/><path d="M29 35h14M29 42h9" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="3"/>',
    user: '<circle cx="36" cy="29" r="7" fill="none" stroke="currentColor" stroke-width="3.5"/><path d="M23 51c1.8-7.2 6.8-11 13-11s11.2 3.8 13 11" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="3.5"/>',
};

function nodeSymbol(kind: JourneyNodeKind, fill: string, stroke: string): string {
    const icon = NODE_ICONS[kind].replaceAll("currentColor", stroke);
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72" viewBox="0 0 72 72"><circle cx="36" cy="36" r="33" fill="${fill}" stroke="${stroke}" stroke-width="3"/>${icon}</svg>`;

    return `image://data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function timeValue(value?: string): number {
    const timestamp = value ? Date.parse(value) : 0;

    return Number.isFinite(timestamp) ? timestamp : 0;
}

function pageValue(value?: string): string {
    return value && value !== "—" ? value : "未知页面";
}

function buildGraph(
    sessions: AuditSessionSummary[],
    tone: ReturnType<typeof useDashboardTheme>["tone"],
    width: number,
    compact: boolean,
) {
    const userStats = new Map<string, UserStat>();
    const pageStats = new Map<string, PageStat>();
    let pageOrder = 0;

    for (const session of sessions) {
        const uid = session.uid ?? session.username ?? "unknown";
        const user = userStats.get(uid) ?? {
            actionCount: 0,
            latest: 0,
            pageCount: 0,
            sessionCount: 0,
            username: session.username,
        };
        user.actionCount += session.actionCount ?? 0;
        user.latest = Math.max(user.latest, timeValue(session.end), timeValue(session.start));
        user.pageCount += session.pageCount ?? 0;
        user.sessionCount += 1;
        userStats.set(uid, user);

        for (const [path, direction] of [
            [pageValue(session.entryPath), "entry"],
            [pageValue(session.exitPath), "exit"],
        ] as const) {
            const page = pageStats.get(path) ?? {
                entryCount: 0,
                exitCount: 0,
                firstOrder: pageOrder++,
                path,
            };
            if (direction === "entry") {
                page.entryCount += 1;
            } else {
                page.exitCount += 1;
            }
            pageStats.set(path, page);
        }
    }

    const orderedUsers = [...userStats.entries()].sort((left, right) => right[1].latest - left[1].latest);
    const orderedSessions = orderedUsers.flatMap(([uid]) =>
        sessions
            .filter((session) => (session.uid ?? session.username ?? "unknown") === uid)
            .sort((left, right) => timeValue(left.start) - timeValue(right.start)),
    );
    const pages = [...pageStats.values()].sort((left, right) => left.firstOrder - right.firstOrder);
    const rowCount = Math.max(orderedSessions.length, pages.length, orderedUsers.length, 3);
    const height = Math.max(compact ? 460 : 520, 120 + rowCount * (compact ? 30 : 34));
    const rowY = (index: number, count: number) =>
        count <= 1 ? height / 2 : 32 + (index * (height - 64)) / (count - 1);
    const graphWidth = Math.max(width, 280);
    const columns = {
        page: graphWidth * 0.78,
        session: graphWidth * 0.42,
        user: Math.max(30, graphWidth * 0.09),
    };
    const userPalette = tone("blue");
    const sessionPalette = tone("geekblue");
    const pagePalette = tone("gold");
    const nodes: JourneyGraphNode[] = [];
    const edges: JourneyGraphEdge[] = [];
    const userYs = new Map<string, number[]>();

    orderedSessions.forEach((session, index) => {
        const uid = session.uid ?? session.username ?? "unknown";
        const sessionId = `session:${session.sessionId ?? `${uid}:${session.start ?? index}`}`;
        const userNode = `user:${uid}`;
        const y = rowY(index, orderedSessions.length);
        const entry = pageValue(session.entryPath);
        const exit = pageValue(session.exitPath);
        const actions = session.actionCount ?? 0;

        userYs.set(uid, [...(userYs.get(uid) ?? []), y]);
        nodes.push({
            detail: `${entry} → ${exit}\n${formatCount(session.pageCount ?? 0)} 页 · ${formatCount(actions)} 次操作\n${formatJourneyDuration(session.durationMs ?? 0)} · ${formatDateTime(session.start)} → ${formatDateTime(session.end)}`,
            fill: sessionPalette.soft,
            id: sessionId,
            kind: "session",
            size: compact
                ? Math.min(36, 26 + Math.log2(actions + 1) * 1.5)
                : Math.min(42, 30 + Math.log2(actions + 1) * 1.8),
            stroke: sessionPalette.base,
            title: `会话 · ${userLabel(session.username ?? uid)}`,
            x: columns.session,
            y,
        });
        edges.push({
            color: userPalette.base,
            count: 1,
            id: `${userNode}>${sessionId}`,
            kind: "user-session",
            source: userNode,
            target: sessionId,
        });

        for (const path of new Set([entry, exit])) {
            edges.push({
                color: pagePalette.base,
                count: 1,
                id: `${sessionId}>page:${path}`,
                kind: "session-page",
                source: sessionId,
                target: `page:${path}`,
            });
        }
    });

    orderedUsers.forEach(([uid, stat]) => {
        const ys = userYs.get(uid) ?? [height / 2];
        const y = ys.reduce((sum, value) => sum + value, 0) / ys.length;

        nodes.push({
            detail: `${formatCount(stat.sessionCount)} 个会话 · ${formatCount(stat.pageCount)} 页 · ${formatCount(stat.actionCount)} 次操作`,
            fill: userPalette.soft,
            id: `user:${uid}`,
            kind: "user",
            size: compact ? 46 : 54,
            stroke: userPalette.base,
            title: userLabel(stat.username ?? uid),
            x: columns.user,
            y,
        });
    });

    pages.forEach((page, index) => {
        const activity = page.entryCount + page.exitCount;

        nodes.push({
            detail: `${formatCount(page.entryCount)} 个会话进入 · ${formatCount(page.exitCount)} 个会话离开`,
            fill: pagePalette.soft,
            id: `page:${page.path}`,
            kind: "page",
            size: compact
                ? Math.min(28, 22 + Math.log2(activity + 1) * 1.8)
                : Math.min(34, 26 + Math.log2(activity + 1) * 2.2),
            stroke: pagePalette.base,
            title: page.path,
            x: columns.page,
            y: rowY(index, pages.length),
        });
    });

    return { compact, edges, height, nodes };
}

export function JourneyRelationshipGraph({ sessions }: JourneyRelationshipGraphProps) {
    const { token, tone } = useDashboardTheme();
    const isMobile = useMobile();
    const { ref, width } = useResizeObserver<HTMLDivElement>();
    const compact = isMobile || width < 560;
    const graph = useMemo(() => buildGraph(sessions, tone, width, compact), [compact, sessions, tone, width]);
    const option = useMemo<EChartsCoreOption>(() => {
        const categories: JourneyNodeKind[] = ["user", "session", "page"];
        const tooltipWidth = Math.max(148, Math.min(graph.compact ? 208 : 260, width - 28));
        const categoryIndex: Record<JourneyNodeKind, number> = {
            page: 2,
            session: 1,
            user: 0,
        };

        return {
            animationDuration: 360,
            series: [
                {
                    blur: {
                        itemStyle: { opacity: 0.22 },
                        lineStyle: { opacity: 0.08 },
                    },
                    categories: categories.map((kind) => ({
                        itemStyle: {
                            color:
                                kind === "user"
                                    ? tone("blue").base
                                    : kind === "session"
                                      ? tone("geekblue").base
                                      : tone("gold").base,
                        },
                        name: NODE_KIND_LABELS[kind],
                    })),
                    data: graph.nodes.map((node) => ({
                        category: categoryIndex[node.kind],
                        detail: node.detail,
                        id: node.id,
                        kind: node.kind,
                        name: NODE_KIND_LABELS[node.kind],
                        symbol: nodeSymbol(node.kind, node.fill, node.stroke),
                        symbolKeepAspect: true,
                        symbolSize: node.size,
                        title: node.title,
                        x: node.x,
                        y: node.y,
                    })),
                    draggable: false,
                    edgeSymbol: ["none", "arrow"],
                    edgeSymbolSize: 5,
                    emphasis: {
                        focus: "adjacency",
                        label: { show: false },
                        lineStyle: { opacity: 0.92, width: 2.2 },
                    },
                    label: { show: false },
                    layout: "none",
                    lineStyle: { curveness: 0.08, opacity: 0.5 },
                    links: graph.edges.map((edge) => ({
                        count: edge.count,
                        kind: edge.kind,
                        lineStyle: {
                            color: edge.color,
                            curveness: 0.08,
                            opacity: 0.46,
                            width: Math.min(3.2, 1 + Math.log2(edge.count + 1) * 0.7),
                        },
                        source: edge.source,
                        target: edge.target,
                    })),
                    roam: false,
                    type: "graph",
                },
            ],
            textStyle: { fontFamily: "Noto Sans Variable, sans-serif" },
            tooltip: {
                backgroundColor: token.colorBgElevated,
                borderColor: token.colorBorderSecondary,
                borderWidth: 1,
                confine: true,
                extraCssText: `width:${tooltipWidth}px;max-width:calc(100% - 12px);box-sizing:border-box;border-radius:${token.borderRadiusLG};box-shadow:${token.boxShadowSecondary};padding:0;white-space:normal;overflow-wrap:anywhere;`,
                formatter: (raw: unknown) => {
                    const params = raw as {
                        data?: {
                            count?: number;
                            detail?: string;
                            kind?: JourneyNodeKind | "session-page" | "user-session";
                            title?: string;
                        };
                        dataType?: "edge" | "node";
                    };

                    if (params.dataType === "edge") {
                        return `<div style="padding:10px 12px"><div style="color:${token.colorTextTertiary};font-size:11px;font-weight:600">关系</div><div style="margin-top:5px;color:${token.colorText};font-size:12px">${escapeHtml(params.data?.kind === "user-session" ? "用户 · 会话" : "会话 · 页面")}</div><div style="margin-top:5px;color:${token.colorTextSecondary};font-size:11px">${formatCount(params.data?.count ?? 0)} 次关联</div></div>`;
                    }

                    const kind = params.data?.kind;
                    const nodeKind = kind === "page" || kind === "session" || kind === "user" ? kind : undefined;

                    return `<div style="padding:12px 14px"><div style="color:${token.colorTextTertiary};font-size:11px;font-weight:600">${escapeHtml(nodeKind ? NODE_KIND_LABELS[nodeKind] : "节点")}</div><div style="margin-top:6px;color:${token.colorText};font-size:13px;font-weight:600;line-height:1.45;white-space:normal;overflow-wrap:anywhere;word-break:break-word">${escapeHtml(params.data?.title ?? "")}</div><div style="margin-top:7px;color:${token.colorTextSecondary};font-size:11px;line-height:1.6;white-space:pre-line;overflow-wrap:anywhere;word-break:break-word">${escapeHtml(params.data?.detail ?? "")}</div></div>`;
                },
                trigger: "item",
            },
        };
    }, [graph, token, tone, width]);

    return (
        <div className="min-w-0 overflow-hidden" ref={ref}>
            <div className="max-h-[560px] overflow-x-hidden overflow-y-auto sm:max-h-[760px]">
                <EChart height={graph.height} option={option} />
            </div>
        </div>
    );
}
