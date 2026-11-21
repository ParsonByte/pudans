"use client";

import { useMemo } from "react";

import type { EChartsOption } from "echarts";

import {
  chartAxisLabel,
  chartGrid,
  chartSplitLine,
  chartTooltip,
} from "@/components/charts/chart-options";
import { useChartTokens } from "@/components/charts/chart-theme";
import { EChart } from "@/components/charts/echart";
import { formatTrendDay } from "@/lib/date";

type ActivityBarChartPoint = {
  date?: string;
  count?: number;
};

type ActivityBarChartProps = {
  points: ActivityBarChartPoint[];
  seriesLabel?: string;
  height?: number;
  formatLabel?: (value: string) => string;
};

export function ActivityBarChart({
  points,
  seriesLabel = "操作次数",
  height = 300,
  formatLabel = formatTrendDay,
}: ActivityBarChartProps) {
  const tokens = useChartTokens();
  const data = useMemo(
    () =>
      points
        .map((point) => ({ date: point.date ?? "", count: point.count ?? 0 }))
        .filter((point) => point.date)
        .sort((left, right) => left.date.localeCompare(right.date)),
    [points],
  );
  const option = useMemo<EChartsOption>(
    () => ({
      grid: chartGrid(),
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
          const rows = items.map(
            (item) =>
              `${item.marker ?? ""}${item.seriesName ?? ""}：${Number(
                item.value ?? 0,
              ).toLocaleString("zh-CN")}`,
          );
          return `${formatLabel(String(first.name ?? ""))}<br/>${rows.join("<br/>")}`;
        },
      },
      xAxis: {
        type: "category",
        data: data.map((point) => point.date),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value: string) => formatLabel(value),
        },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: chartAxisLabel(tokens),
        splitLine: chartSplitLine(tokens),
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [
        {
          type: "bar",
          name: seriesLabel,
          data: data.map((point) => point.count),
          itemStyle: { color: tokens.primary },
          barMaxWidth: 24,
        },
      ],
    }),
    [data, formatLabel, seriesLabel, tokens],
  );

  return (
    <div className="w-full p-4 pt-5" style={{ height }}>
      <EChart option={option} height="100%" ariaLabel={seriesLabel} />
    </div>
  );
}
