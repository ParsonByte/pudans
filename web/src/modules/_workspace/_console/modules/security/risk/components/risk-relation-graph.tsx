"use client";

import { useMemo } from "react";

import type { RiskRelation, RiskSceneStat } from "@pudans-dev/api-client";
import { Empty } from "tdesign-react";

import { EChart } from "@/components/ui";
import type { EChartsCoreOption } from "@/components/ui/echart";
import { useMobile } from "@/hooks/ui/use-mobile";

import { formatCount } from "../../../../composables/_format";
import { escapeHtml } from "../../../../composables/_html";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { sceneLabel } from "../composables/_risk";

type RiskRelationGraphProps = {
    relations?: RiskRelation[];
    scenes?: RiskSceneStat[];
};

type GraphPalette = {
    background: string;
    border: string;
    edgeAlert: string;
    edgeBlocked: string;
    scene: string;
    sceneSoft: string;
    subject: string;
    subjectSoft: string;
    text: string;
    textSecondary: string;
};

type GraphNode = {
    category: number;
    id: string;
    itemStyle: {
        borderColor: string;
        borderWidth: number;
        color: string;
    };
    label: string;
    name: string;
    symbolSize: number;
    value: number;
};

type GraphLink = {
    blocked: number;
    events: number;
    lineStyle: {
        color: string;
        opacity: number;
        width: number;
    };
    scene: string;
    source: string;
    subject: string;
    target: string;
    value: number;
};

type GraphData = {
    links: GraphLink[];
    nodes: GraphNode[];
};

function buildGraphData(relations: RiskRelation[], scenes: RiskSceneStat[], palette: GraphPalette): GraphData {
    const sceneTotals = new Map<string, number>();
    const subjectTotals = new Map<string, number>();
    for (const relation of relations) {
        const scene = relation.scene ?? "unknown";
        const subject = relation.subject ?? "unknown";
        sceneTotals.set(scene, (sceneTotals.get(scene) ?? 0) + (relation.events ?? 0));
        subjectTotals.set(subject, (subjectTotals.get(subject) ?? 0) + (relation.events ?? 0));
    }

    const nodes = new Map<string, GraphNode>();
    const links = new Map<string, GraphLink>();

    for (const relation of relations) {
        const scene = relation.scene ?? "unknown";
        const subject = relation.subject ?? "unknown";
        const sceneId = `scene:${scene}`;
        const subjectId = `subject:${subject}`;
        const sceneEvents = sceneTotals.get(scene) ?? 0;
        const subjectEvents = subjectTotals.get(subject) ?? 0;

        if (!nodes.has(sceneId)) {
            const label = sceneLabel(scene, scenes);
            nodes.set(sceneId, {
                category: 0,
                id: sceneId,
                itemStyle: {
                    borderColor: palette.scene,
                    borderWidth: 2,
                    color: palette.sceneSoft,
                },
                label: `${label}\n${formatCount(sceneEvents)} 次`,
                name: label,
                symbolSize: Math.min(54, 28 + Math.log2(sceneEvents + 1) * 5),
                value: sceneEvents,
            });
        }

        if (!nodes.has(subjectId)) {
            nodes.set(subjectId, {
                category: 1,
                id: subjectId,
                itemStyle: {
                    borderColor: palette.subject,
                    borderWidth: 1.8,
                    color: palette.subjectSoft,
                },
                label: `${subject}\n${formatCount(subjectEvents)} 次`,
                name: subject,
                symbolSize: Math.min(44, 24 + Math.log2(subjectEvents + 1) * 4),
                value: subjectEvents,
            });
        }

        const events = relation.events ?? 0;
        const blocked = relation.blocked ?? 0;
        links.set(`${sceneId}>${subjectId}`, {
            blocked,
            events,
            lineStyle: {
                color: blocked > 0 ? palette.edgeBlocked : palette.edgeAlert,
                opacity: 0.48,
                width: Math.min(4.2, 1.1 + Math.log2(events + 1) * 0.7),
            },
            scene,
            source: sceneId,
            subject,
            target: subjectId,
            value: events,
        });
    }

    return { links: [...links.values()], nodes: [...nodes.values()] };
}

export function RiskRelationGraph({ relations, scenes }: RiskRelationGraphProps) {
    const isMobile = useMobile();
    const { token: tokens, tone } = useDashboardTheme();
    const palette = useMemo<GraphPalette>(() => {
        const scene = tone("red");
        const subject = tone("gold");

        return {
            background: tokens.colorBgContainer,
            border: tokens.colorBorderSecondary,
            edgeAlert: subject.base,
            edgeBlocked: scene.base,
            scene: scene.base,
            sceneSoft: scene.soft,
            subject: subject.base,
            subjectSoft: subject.soft,
            text: tokens.colorText,
            textSecondary: tokens.colorTextSecondary,
        };
    }, [tokens, tone]);
    const data = useMemo(() => buildGraphData(relations ?? [], scenes ?? [], palette), [palette, relations, scenes]);
    const option = useMemo<EChartsCoreOption>(
        () => ({
            series: [
                {
                    bottom: 18,
                    categories: [{ name: "风险场景" }, { name: "受影响主体" }],
                    data: data.nodes,
                    draggable: true,
                    edgeLabel: { show: false },
                    emphasis: { focus: "adjacency", lineStyle: { opacity: 0.85 } },
                    force: {
                        edgeLength: isMobile ? 90 : 138,
                        gravity: 0.08,
                        repulsion: isMobile ? 130 : 210,
                    },
                    label: {
                        color: palette.text,
                        fontSize: 11,
                        formatter: (params: { data?: GraphNode }) => params.data?.label ?? "",
                        lineHeight: 15,
                        position: "bottom",
                        show: true,
                    },
                    layout: "force",
                    left: 18,
                    lineStyle: { opacity: 0.5 },
                    links: data.links,
                    roam: true,
                    right: 18,
                    top: 18,
                    type: "graph",
                },
            ],
            tooltip: {
                backgroundColor: palette.background,
                borderColor: palette.border,
                formatter: (params: { data?: GraphLink; dataType?: string }) => {
                    if (params.dataType === "node" && params.data) {
                        const node = params.data as unknown as GraphNode;

                        return `<div style="min-width:180px;font-size:12px;line-height:1.7">
                            <div style="font-weight:600;margin-bottom:4px">${escapeHtml(node.name)}</div>
                            <div style="color:${palette.textSecondary}">${node.category === 0 ? "风险场景" : "受影响主体"} · ${formatCount(node.value)} 次</div>
                        </div>`;
                    }

                    if (params.dataType !== "edge" || !params.data) {
                        return "";
                    }

                    const link = params.data;

                    return `<div style="min-width:220px;font-size:12px;line-height:1.7">
                        <div style="font-weight:600;margin-bottom:4px">${escapeHtml(sceneLabel(link.scene, scenes))} → ${escapeHtml(link.subject)}</div>
                        <div style="color:${palette.textSecondary}">事件 ${formatCount(link.events)} 次 · 阻断 ${formatCount(link.blocked)} 次</div>
                    </div>`;
                },
                textStyle: { color: palette.text, fontSize: 12 },
            },
        }),
        [data, isMobile, palette, scenes],
    );

    if (!relations?.length) {
        return <Empty description="暂无场景与主体关系" size="small" />;
    }

    const sceneCount = data.nodes.filter((node) => node.category === 0).length;
    const subjectCount = data.nodes.filter((node) => node.category === 1).length;
    const eventCount = relations.reduce((sum, relation) => sum + (relation.events ?? 0), 0);

    return (
        <div className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px]">
                <span style={{ color: tokens.colorTextSecondary }}>
                    场景{" "}
                    <strong className="tabular-nums" style={{ color: tokens.colorText }}>
                        {formatCount(sceneCount)}
                    </strong>
                </span>
                <span style={{ color: tokens.colorTextSecondary }}>
                    主体{" "}
                    <strong className="tabular-nums" style={{ color: tokens.colorText }}>
                        {formatCount(subjectCount)}
                    </strong>
                </span>
                <span style={{ color: tokens.colorTextSecondary }}>
                    关系{" "}
                    <strong className="tabular-nums" style={{ color: tokens.colorText }}>
                        {formatCount(data.links.length)}
                    </strong>
                </span>
                <span style={{ color: tokens.colorTextSecondary }}>
                    事件{" "}
                    <strong className="tabular-nums" style={{ color: tokens.colorText }}>
                        {formatCount(eventCount)}
                    </strong>
                </span>
                {relations.length >= 80 ? <span style={{ color: tokens.colorWarning }}>最近 80 条关系</span> : null}
            </div>
            <div
                className="overflow-hidden rounded-2xl border"
                style={{ background: tokens.colorFillQuaternary, borderColor: tokens.colorBorderSecondary }}
            >
                <EChart height={isMobile ? 420 : 500} option={option} />
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px]">
                <span className="flex items-center gap-1.5" style={{ color: tokens.colorTextSecondary }}>
                    <span className="h-2 w-2 rounded-full" style={{ background: palette.scene }} />
                    风险场景
                </span>
                <span className="flex items-center gap-1.5" style={{ color: tokens.colorTextSecondary }}>
                    <span className="h-2 w-2 rounded-full" style={{ background: palette.subject }} />
                    受影响主体
                </span>
            </div>
        </div>
    );
}
