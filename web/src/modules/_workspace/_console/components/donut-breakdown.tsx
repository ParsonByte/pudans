"use client";

import { useMemo } from "react";

import { Empty } from "tdesign-react";

import { EChart, type EChartsCoreOption } from "@/components/ui";

import { formatCount, formatPercent } from "../composables/_format";
import { escapeHtml } from "../composables/_html";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type DonutSlice = {
    color: string;
    key: string;
    label: string;
    value: number;
};

type DonutBreakdownProps = {
    centerLabel: string;
    emptyText: string;
    layout?: "horizontal" | "vertical";
    size?: "compact" | "regular";
    slices: DonutSlice[];
    valueUnit: string;
};

const SIZE_STYLES = {
    compact: {
        chart: 160,
        container: "size-40",
    },
    regular: {
        chart: 192,
        container: "size-48",
    },
} as const;

export function DonutBreakdown({
    centerLabel,
    emptyText,
    layout = "vertical",
    size = "regular",
    slices,
    valueUnit,
}: DonutBreakdownProps) {
    const { token } = useDashboardTheme();
    const total = slices.reduce((sum, item) => sum + item.value, 0);
    const option = useMemo<EChartsCoreOption>(
        () => ({
            series: [
                {
                    avoidLabelOverlap: true,
                    data: slices.map((item) => ({
                        itemStyle: { color: item.color },
                        name: item.label,
                        value: item.value,
                    })),
                    emphasis: { scale: true, scaleSize: 4 },
                    itemStyle: { borderColor: token.colorBgContainer, borderRadius: 7, borderWidth: 2 },
                    label: { show: false },
                    labelLine: { show: false },
                    radius: ["64%", "88%"],
                    type: "pie",
                },
            ],
            textStyle: { fontFamily: "Noto Sans Variable, sans-serif" },
            title: {
                left: "center",
                subtext: centerLabel,
                subtextStyle: {
                    color: token.colorTextSecondary,
                    fontFamily: "Noto Sans Variable, sans-serif",
                    fontSize: 12,
                },
                text: formatCount(total),
                textStyle: {
                    color: token.colorText,
                    fontFamily: "Noto Sans Variable, sans-serif",
                    fontSize: 24,
                    fontWeight: 600,
                },
                top: "38%",
            },
            tooltip: {
                backgroundColor: "transparent",
                borderWidth: 0,
                confine: true,
                formatter: (parameters: { dataIndex?: number }[]) => {
                    const item = slices[parameters[0]?.dataIndex ?? 0];

                    if (!item) {
                        return "";
                    }

                    return `<div style="min-width:148px;padding:10px 12px;border:1px solid var(--td-component-stroke);border-radius:6px;background:var(--td-bg-color-container);box-shadow:var(--td-shadow-2);font-size:12px;color:var(--td-text-color-secondary)"><div style="display:flex;align-items:center;justify-content:space-between;gap:20px"><span style="display:flex;align-items:center;gap:8px"><span style="width:8px;height:8px;border-radius:50%;background:${item.color}"></span>${escapeHtml(item.label)}</span><strong style="color:var(--td-text-color-primary)">${formatCount(item.value)} ${escapeHtml(valueUnit)}</strong></div><div style="margin-top:6px;text-align:right">${formatPercent(item.value, total)}</div></div>`;
                },
                padding: 0,
                trigger: "item",
            },
        }),
        [centerLabel, slices, token, total, valueUnit],
    );

    if (slices.length === 0) {
        return <Empty description={emptyText} size="small" />;
    }

    const dimensions = SIZE_STYLES[size];

    return (
        <div
            className={
                layout === "horizontal"
                    ? "flex flex-col items-center gap-5 xl:flex-row"
                    : "flex flex-col items-center gap-5"
            }
        >
            <div className={`relative shrink-0 ${dimensions.container}`}>
                <EChart height={dimensions.chart} option={option} />
            </div>
            <div className="w-full min-w-0 flex-1 space-y-3">
                {slices.map((item) => (
                    <div className="flex items-center gap-3" key={item.key}>
                        <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: item.color }} />
                        <span className="min-w-0 flex-1 truncate text-sm" style={{ color: token.colorTextSecondary }}>
                            {item.label}
                        </span>
                        <span
                            className="w-14 text-right text-xs font-medium tabular-nums"
                            style={{ color: token.colorTextSecondary }}
                        >
                            {formatPercent(item.value, total)}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}
