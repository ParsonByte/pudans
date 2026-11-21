"use client";

import { useMemo } from "react";

import type { EChartsOption } from "echarts";

import {
  chartAxisLabel,
  chartGrid,
  chartLegend,
  chartSplitLine,
  chartTooltip,
} from "@/components/charts/chart-options";
import { type ChartTokens, useChartTokens } from "@/components/charts/chart-theme";
import { EChart } from "@/components/charts/echart";
import { formatTrendDay } from "@/lib/date";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { UsageTrend, UsageTrendMetric } from "../lib/analytics";

export type UsageTrendChartMode = "bar" | "line";

const SERIES_TOKEN_BY_VARIABLE: Record<string, keyof ChartTokens> = {
  "--chart-1": "chart1",
  "--chart-2": "chart2",
  "--chart-3": "chart3",
  "--chart-4": "chart4",
  "--chart-5": "chart5",
  "--muted-foreground": "mutedForeground",
};

function resolveSeriesColor(color: string, tokens: ChartTokens): string {
  const variable = /^var\((--[a-z0-9-]+)\)$/.exec(color)?.[1];
  const token = variable ? SERIES_TOKEN_BY_VARIABLE[variable] : undefined;
  return token ? tokens[token] : color;
}

type RequestTrendChartProps = {
  trend: UsageTrend;
  metric: UsageTrendMetric;
  mode: UsageTrendChartMode;
  onSelectDay?: (day: string) => void;
};

function formatTrendMetricValue(metric: UsageTrendMetric, value: number): string {
  if (!Number.isFinite(value)) {
    return "—";
  }
  if (metric === "tokens") {
    return formatTokenCount(value);
  }
  if (metric === "units") {
    return formatTokenCount(value);
  }
  if (metric === "speed") {
    return `${formatNumber(value, 1)} token/秒`;
  }
  return `${formatNumber(value)} 次`;
}

export function RequestTrendChart({ trend, metric, mode, onSelectDay }: RequestTrendChartProps) {
  const tokens = useChartTokens();
  const option = useMemo<EChartsOption>(() => {
    const resolved = trend.series.map((entry) => ({
      ...entry,
      resolvedColor: resolveSeriesColor(entry.color, tokens),
    }));
    const splitLine = chartSplitLine(tokens);
    const chartSeries: EChartsOption["series"] =
      mode === "bar"
        ? resolved.map((entry, index) => ({
            type: "bar",
            name: entry.label,
            data: trend.points.map((point) => Number(point[entry.key] ?? 0)),
            stack: metric === "speed" ? undefined : "trend",
            itemStyle: {
              color: entry.resolvedColor,
              borderRadius: metric === "speed" || index === resolved.length - 1 ? [3, 3, 0, 0] : 0,
            },
          }))
        : resolved.map((entry) => ({
            type: "line",
            name: entry.label,
            data: trend.points.map((point) => Number(point[entry.key] ?? 0)),
            smooth: true,
            showSymbol: false,
            symbolSize: 6,
            lineStyle: { color: entry.resolvedColor, width: 2 },
            itemStyle: { color: entry.resolvedColor },
          }));
    return {
      grid: { ...chartGrid(), bottom: 36 },
      tooltip: {
        ...chartTooltip(tokens),
        trigger: "axis",
        axisPointer: { type: mode === "bar" ? "shadow" : "line" },
        formatter: (params) => {
          const items = Array.isArray(params) ? params : [params];
          const first = items[0];
          if (!first) {
            return "";
          }
          const entries = new Map(items.map((item) => [String(item.seriesName ?? ""), item]));
          const rows = resolved.map((entry) => {
            const item = entries.get(entry.label);
            const value = formatTrendMetricValue(metric, Number(item?.value ?? 0));
            return `${item?.marker ?? ""}${entry.label}：${value}`;
          });
          return `${formatTrendDay(String(first.name ?? ""))}<br/>${rows.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        boundaryGap: mode === "bar",
        data: trend.points.map((point) => point.day),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTrendDay(value),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTrendMetricValue(metric, value),
        },
        splitLine: {
          ...splitLine,
          lineStyle: { ...splitLine.lineStyle, type: "dashed" },
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: chartSeries,
    };
  }, [metric, mode, tokens, trend.points, trend.series]);
  const events = useMemo(
    () => ({
      click: (params: unknown) => {
        const dataIndex = (params as { dataIndex?: number } | null)?.dataIndex;
        const point = typeof dataIndex === "number" ? trend.points[dataIndex] : undefined;
        if (point && onSelectDay) {
          onSelectDay(point.day);
        }
      },
    }),
    [onSelectDay, trend.points],
  );

  return (
    <div className="h-[320px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="请求趋势" events={events} />
    </div>
  );
}
