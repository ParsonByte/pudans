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
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { UsageBucket, UsageDaily } from "../lib/api";
import { formatUsageBucketLabel } from "../lib/model";

export type UsageTokenChartMode = "area" | "bar";

type UsageTokenSeries = {
  key: "inputTokens" | "cacheTokens" | "outputTokens";
  label: string;
  token: keyof ChartTokens;
};

const SERIES: UsageTokenSeries[] = [
  { key: "inputTokens", label: "输入", token: "chart2" },
  { key: "cacheTokens", label: "缓存", token: "chart3" },
  { key: "outputTokens", label: "输出", token: "chart1" },
];

type UsageTokenChartProps = {
  series: UsageDaily[];
  mode: UsageTokenChartMode;
  bucket?: UsageBucket;
};

export function UsageTokenChart({ series, mode, bucket = "day" }: UsageTokenChartProps) {
  const tokens = useChartTokens();
  const option = useMemo<EChartsOption>(() => {
    const commonSeries = SERIES.map((entry) => ({
      name: entry.label,
      data: series.map((point) => point[entry.key]),
      stack: "tokens",
      itemStyle: { color: tokens[entry.token] },
    }));
    const chartSeries: EChartsOption["series"] =
      mode === "area"
        ? commonSeries.map((entry) => ({
            ...entry,
            type: "line",
            smooth: true,
            showSymbol: false,
            symbolSize: 6,
            lineStyle: { color: entry.itemStyle.color, width: 2 },
            areaStyle: { color: entry.itemStyle.color, opacity: 0.35 },
          }))
        : commonSeries.map((entry, index) => ({
            ...entry,
            type: "bar",
            barMaxWidth: 24,
            itemStyle: {
              ...entry.itemStyle,
              borderRadius: index === commonSeries.length - 1 ? [4, 4, 0, 0] : 0,
            },
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
          const rows = SERIES.map((series) => {
            const item = entries.get(series.label);
            const value = Number(item?.value ?? 0).toLocaleString("zh-CN");
            return `${item?.marker ?? ""}${series.label}：${value}`;
          });
          return `${formatUsageBucketLabel(String(first.name ?? ""), bucket)}<br/>${rows.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        boundaryGap: mode === "bar",
        data: series.map((point) => point.day),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatUsageBucketLabel(value, bucket),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTokenCount(value),
        },
        splitLine: chartSplitLine(tokens),
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: chartSeries,
    };
  }, [bucket, mode, series, tokens]);

  return (
    <div className="h-[300px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="Token 用量趋势" />
    </div>
  );
}
