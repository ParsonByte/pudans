"use client";

import { useMemo } from "react";

import { Empty } from "tdesign-react";

import { EChart, type EChartsCoreOption } from "@/components/ui";

import { formatCount } from "../composables/_format";
import { escapeHtml } from "../composables/_html";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

export type DistributionBarDatum = {
    color: string;
    key: string;
    label: string;
    tick: string;
    value: number;
};

type DistributionBarChartProps = {
    data: DistributionBarDatum[];
    emptyText: string;
    title: string;
    valueLabel: string;
};

export function DistributionBarChart({ data, emptyText, title, valueLabel }: DistributionBarChartProps) {
    const { token } = useDashboardTheme();
    const option = useMemo<EChartsCoreOption>(
        () => ({
            grid: { bottom: 8, left: 4, right: 8, top: 24 },
            series: [
                {
                    barMaxWidth: 44,
                    data: data.map((item) => ({
                        itemStyle: { borderRadius: [6, 6, 0, 0], color: item.color },
                        value: item.value,
                    })),
                    name: title,
                    type: "bar",
                },
            ],
            textStyle: { fontFamily: "Noto Sans Variable, sans-serif" },
            tooltip: {
                backgroundColor: "transparent",
                borderWidth: 0,
                confine: true,
                formatter: (parameters: { dataIndex?: number; value?: number | string }[]) => {
                    const item = data[parameters[0]?.dataIndex ?? 0];

                    if (!item) {
                        return "";
                    }

                    return `<div style="min-width:148px;padding:10px 12px;border:1px solid var(--td-component-stroke);border-radius:6px;background:var(--td-bg-color-container);box-shadow:var(--td-shadow-2);font-size:12px;color:var(--td-text-color-secondary)">${escapeHtml(title)}<div style="display:flex;align-items:center;justify-content:space-between;gap:20px;margin-top:6px"><span style="display:flex;align-items:center;gap:8px"><span style="width:8px;height:8px;border-radius:50%;background:${item.color}"></span>${escapeHtml(item.label)}</span><strong style="color:var(--td-text-color-primary)">${formatCount(item.value)} ${escapeHtml(valueLabel)}</strong></div></div>`;
                },
                padding: 0,
                trigger: "axis",
            },
            xAxis: {
                axisLabel: { color: token.colorTextTertiary, fontSize: 10, interval: 0 },
                axisLine: { lineStyle: { color: token.colorBorderSecondary } },
                axisTick: { show: false },
                data: data.map((item) => item.tick),
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
        }),
        [data, title, token, valueLabel],
    );

    if (data.every((item) => item.value === 0)) {
        return <Empty description={emptyText} size="small" />;
    }

    return <EChart height={240} option={option} />;
}
