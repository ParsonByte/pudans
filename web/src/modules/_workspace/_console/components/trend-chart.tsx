"use client";

import { useMemo } from "react";

import { Empty } from "tdesign-react";

import { EChart, type EChartsCoreOption } from "@/components/ui";
import { useMobile } from "@/hooks/ui/use-mobile";

import { formatCount, formatDateLabel } from "../composables/_format";
import { escapeHtml } from "../composables/_html";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type TrendChartPoint = {
    date?: string;
    label: string;
};

type TrendSeries = {
    color: string;
    key: string;
    label: string;
    lowerIsBetter?: boolean;
    type: "area" | "line";
};

type TrendChartProps = {
    data: TrendChartPoint[];
    emptyText: string;
    height?: number;
    series: TrendSeries[];
};

type AxisTooltipParameter = {
    axisValue?: string;
    color?: string;
    dataIndex?: number;
    marker?: string;
    seriesName?: string;
    value?: number | string;
};

type TooltipRow = {
    color: string;
    deltaColor: string;
    deltaText: string;
    label: string;
    value: string;
};

function resolveChartColor(color: string): string {
    let resolved = color.trim();

    if (typeof window === "undefined") {
        return resolved.startsWith("var(") ? "#0052d9" : resolved;
    }

    for (let depth = 0; depth < 4 && resolved.startsWith("var("); depth += 1) {
        const variable = resolved.slice(4, -1).trim();
        const next = window.getComputedStyle(document.documentElement).getPropertyValue(variable).trim();

        if (!next) {
            return "#0052d9";
        }

        resolved = next;
    }

    return resolved.startsWith("var(") ? "#0052d9" : resolved;
}

function chartTooltipMarkup(title: string, rows: TooltipRow[]): string {
    const body = rows
        .map(
            (row) =>
                `<div style="display:grid;grid-template-columns:8px minmax(0,1fr) auto 48px;align-items:center;gap:8px;font-size:12px"><span style="width:8px;height:8px;border-radius:50%;background:${row.color}"></span><span style="overflow:hidden;color:var(--td-text-color-secondary);text-overflow:ellipsis;white-space:nowrap">${escapeHtml(row.label)}</span><strong style="text-align:right;color:var(--td-text-color-primary);font-variant-numeric:tabular-nums;font-weight:600">${escapeHtml(row.value)}</strong><span style="text-align:right;color:${row.deltaColor};font-size:11px;font-variant-numeric:tabular-nums">${escapeHtml(row.deltaText)}</span></div>`,
        )
        .join("");

    return `<div style="width:252px;padding:12px 14px;border:1px solid var(--td-component-stroke);border-radius:8px;background:var(--td-bg-color-container);box-shadow:var(--td-shadow-2)"><div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:9px;margin-bottom:9px;border-bottom:1px solid var(--td-component-stroke)"><strong style="color:var(--td-text-color-primary);font-size:12px;font-weight:600">${escapeHtml(title)}</strong><span style="color:var(--td-text-color-placeholder);font-size:11px">较前一日</span></div><div style="display:grid;gap:8px">${body}</div></div>`;
}

function dailyDelta(
    value: number,
    previousValue: number | undefined,
    lowerIsBetter: boolean,
): { color: string; text: string } {
    if (previousValue === undefined) {
        return { color: "var(--td-text-color-placeholder)", text: "—" };
    }

    const difference = value - previousValue;

    if (difference === 0) {
        return { color: "var(--td-text-color-placeholder)", text: "持平" };
    }

    const positive = lowerIsBetter ? difference < 0 : difference > 0;
    const color = positive ? "var(--td-success-color)" : "var(--td-error-color)";

    if (previousValue === 0) {
        return { color, text: `${difference > 0 ? "+" : ""}${formatCount(difference)}` };
    }

    return {
        color,
        text: `${difference > 0 ? "+" : ""}${((difference / previousValue) * 100).toFixed(1)}%`,
    };
}

export function TrendChart({ data, emptyText, height = 320, series }: TrendChartProps) {
    const isMobile = useMobile();
    const { token } = useDashboardTheme();
    const option = useMemo<EChartsCoreOption>(() => {
        const labels = data.map((point) => point.label);
        const resolvedSeries = series.map((item) => ({
            ...item,
            color: resolveChartColor(item.color),
        }));

        return {
            animation: false,
            grid: { bottom: isMobile ? 8 : 4, left: 4, right: 12, top: isMobile ? 44 : 48 },
            legend: {
                data: resolvedSeries.map((item) => item.label),
                icon: "circle",
                itemHeight: 8,
                itemWidth: 8,
                right: isMobile ? "center" : 4,
                textStyle: {
                    color: token.colorTextSecondary,
                    fontFamily: "Noto Sans Variable, sans-serif",
                    fontSize: 12,
                },
                top: 4,
            },
            series: resolvedSeries.map((item) => ({
                data: data.map((point) => Number((point as Record<string, unknown>)[item.key] ?? 0)),
                emphasis: { focus: "series" },
                lineStyle: { color: item.color, width: item.type === "area" ? 2.4 : 2.1 },
                name: item.label,
                showSymbol: false,
                smooth: false,
                symbol: "circle",
                symbolSize: 7,
                type: "line",
                ...(item.type === "area"
                    ? {
                          areaStyle: {
                              color: item.color,
                              opacity: 0.12,
                          },
                          itemStyle: { color: item.color },
                      }
                    : { itemStyle: { color: item.color } }),
            })),
            textStyle: { fontFamily: "Noto Sans Variable, sans-serif" },
            tooltip: {
                axisPointer: {
                    lineStyle: { color: token.colorBorder, type: "dashed" },
                    type: "line",
                },
                backgroundColor: "transparent",
                borderWidth: 0,
                confine: true,
                formatter: (parameters: AxisTooltipParameter[]) => {
                    const first = parameters[0];
                    const dataIndex = first?.dataIndex ?? 0;
                    const date = data[dataIndex]?.date;
                    const title = formatDateLabel(date) || first?.axisValue || "";
                    const parametersBySeries = new Map(parameters.map((item) => [item.seriesName, item]));
                    const rows = resolvedSeries.map((item) => {
                        const parameter = parametersBySeries.get(item.label);
                        const value = Number(parameter?.value ?? 0);
                        const previousValue =
                            dataIndex > 0
                                ? Number((data[dataIndex - 1] as Record<string, unknown>)[item.key] ?? 0)
                                : undefined;
                        const delta = dailyDelta(value, previousValue, item.lowerIsBetter ?? false);

                        return {
                            color: item.color,
                            deltaColor: delta.color,
                            deltaText: delta.text,
                            label: item.label,
                            value: formatCount(value),
                        };
                    });

                    return chartTooltipMarkup(title, rows);
                },
                padding: 0,
                trigger: "axis",
            },
            xAxis: {
                axisLabel: {
                    color: token.colorTextTertiary,
                    fontSize: 11,
                    hideOverlap: true,
                    interval: "auto",
                },
                axisLine: { lineStyle: { color: token.colorBorderSecondary } },
                axisTick: { show: false },
                boundaryGap: false,
                data: labels,
                type: "category",
            },
            yAxis: {
                axisLabel: { color: token.colorTextTertiary, fontSize: 11 },
                axisLine: { show: false },
                axisTick: { show: false },
                minInterval: 1,
                splitLine: { lineStyle: { color: token.colorBorderSecondary, type: "dashed" } },
                type: "value",
            },
        };
    }, [data, isMobile, series, token]);

    if (data.length === 0) {
        return <Empty description={emptyText} size="small" />;
    }

    return (
        <div className="w-full">
            <EChart height={height} option={option} />
        </div>
    );
}
