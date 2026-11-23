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
    type: "area" | "line";
};

type TrendChartProps = {
    data: TrendChartPoint[];
    emptyText: string;
    heightClassName?: string;
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

function chartTooltipMarkup(items: { color?: string; label?: string; value: string }[]): string {
    const rows = items
        .map(
            (item) =>
                `<div style="display:flex;align-items:center;justify-content:space-between;gap:24px;margin-top:6px;font-size:12px"><span style="display:flex;align-items:center;gap:8px;color:var(--td-text-color-secondary)"><span style="width:8px;height:8px;border-radius:50%;background:${item.color ?? "transparent"}"></span>${escapeHtml(item.label ?? "")}</span><strong style="color:var(--td-text-color-primary);font-weight:600">${item.value}</strong></div>`,
        )
        .join("");

    return `<div style="min-width:148px;padding:10px 12px;border:1px solid var(--td-component-stroke);border-radius:6px;background:var(--td-bg-color-container);box-shadow:var(--td-shadow-2)">${rows}</div>`;
}

export function TrendChart({ data, emptyText, heightClassName, series }: TrendChartProps) {
    const isMobile = useMobile();
    const { token } = useDashboardTheme();
    const option = useMemo<EChartsCoreOption>(() => {
        const labels = data.map((point) => point.label);

        return {
            grid: { bottom: isMobile ? 8 : 4, left: 4, right: 12, top: isMobile ? 44 : 48 },
            legend: {
                data: series.map((item) => item.label),
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
            series: series.map((item) => ({
                data: data.map((point) => Number((point as Record<string, unknown>)[item.key] ?? 0)),
                emphasis: { focus: "series" },
                lineStyle: { color: item.color, width: item.type === "area" ? 2.4 : 2.1 },
                name: item.label,
                showSymbol: false,
                smooth: true,
                symbol: "circle",
                symbolSize: 7,
                type: "line",
                ...(item.type === "area"
                    ? {
                          areaStyle: {
                              color: {
                                  colorStops: [
                                      { color: `${item.color}3d`, offset: 0 },
                                      { color: `${item.color}03`, offset: 1 },
                                  ],
                                  type: "linear",
                                  x: 0,
                                  x2: 0,
                                  y: 0,
                                  y2: 1,
                              },
                              opacity: 1,
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
                    const date = data[first?.dataIndex ?? 0]?.date;
                    const title = formatDateLabel(date) || first?.axisValue || "";
                    const rows = [...parameters].reverse().map((item) => ({
                        color: item.color,
                        label: item.seriesName,
                        value: formatCount(Number(item.value ?? 0)),
                    }));

                    return `<div style="font-size:12px;color:var(--td-text-color-secondary)">${escapeHtml(title)}</div>${chartTooltipMarkup(rows)}`;
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
        <div className={heightClassName ?? "h-72 w-full sm:h-80"}>
            <EChart height="100%" option={option} />
        </div>
    );
}
