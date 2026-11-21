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
import { useChartTokens } from "@/components/charts/chart-theme";
import { EChart } from "@/components/charts/echart";
import { formatTrendDay } from "@/lib/date";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";

import type { UsageSessionStatsDay } from "../../lib/api";

const SERIES_LABELS = ["会话数", "消息数"];

export function SessionActivityChart({ series }: { series: UsageSessionStatsDay[] }) {
  const tokens = useChartTokens();
  const option = useMemo<EChartsOption>(() => {
    const splitLine = chartSplitLine(tokens);
    return {
      grid: { ...chartGrid(), bottom: 36 },
      tooltip: {
        ...chartTooltip(tokens),
        trigger: "axis",
        axisPointer: { type: "shadow" },
        formatter: (params) => {
          const items = Array.isArray(params) ? params : [params];
          const first = items[0];
          if (!first) {
            return "";
          }
          const entries = new Map(items.map((item) => [String(item.seriesName ?? ""), item]));
          const rows = SERIES_LABELS.map((label) => {
            const item = entries.get(label);
            const value = Number(item?.value ?? 0).toLocaleString("zh-CN");
            return `${item?.marker ?? ""}${label}：${value}`;
          });
          return `${formatTrendDay(String(first.name ?? ""))}<br/>${rows.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        data: series.map((point) => point.day),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTrendDay(value),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: [
        {
          type: "value",
          minInterval: 1,
          axisLabel: {
            ...chartAxisLabel(tokens),
            formatter: (value) => formatNumber(value),
          },
          splitLine: {
            ...splitLine,
            lineStyle: { ...splitLine.lineStyle, type: "dashed" },
          },
          axisLine: { show: false },
          axisTick: { show: false },
        },
        {
          type: "value",
          position: "right",
          minInterval: 1,
          axisLabel: {
            ...chartAxisLabel(tokens),
            formatter: (value) => formatNumber(value),
          },
          splitLine: { show: false },
          axisLine: { show: false },
          axisTick: { show: false },
        },
      ],
      series: [
        {
          type: "bar" as const,
          name: "会话数",
          data: series.map((point) => point.sessions),
          yAxisIndex: 0,
          barWidth: 18,
          itemStyle: { color: tokens.chart1, borderRadius: [3, 3, 0, 0] },
        },
        {
          type: "line" as const,
          name: "消息数",
          data: series.map((point) => point.messages),
          yAxisIndex: 1,
          smooth: true,
          showSymbol: false,
          symbolSize: 6,
          lineStyle: { color: tokens.chart3, width: 2 },
          itemStyle: { color: tokens.chart3 },
        },
      ],
    };
  }, [series, tokens]);

  return (
    <div className="h-[280px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="会话活跃趋势" />
    </div>
  );
}
