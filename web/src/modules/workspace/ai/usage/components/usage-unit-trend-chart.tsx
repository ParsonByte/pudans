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
import type { AiModelItem } from "@/modules/workspace/ai/providers/lib/api";
import { modelDisplayName } from "@/modules/workspace/ai/providers/lib/model";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { UnitStatsRow } from "../lib/api";

const MAX_MODELS = 5;
const OTHER_MODEL = "__other__";

const SERIES_TOKENS: (keyof ChartTokens)[] = ["chart1", "chart2", "chart3", "chart4", "chart5"];

type UsageUnitTrendChartProps = {
  rows: UnitStatsRow[];
  models?: AiModelItem[];
};

export function UsageUnitTrendChart({ rows, models = [] }: UsageUnitTrendChartProps) {
  const tokens = useChartTokens();
  const { days, series } = useMemo(() => {
    const dayKeys = [...new Set(rows.map((row) => row.day))].sort();
    const totals = new Map<string, number>();
    for (const row of rows) {
      totals.set(row.model, (totals.get(row.model) ?? 0) + row.units);
    }
    const topModels = [...totals.entries()]
      .sort((left, right) => right[1] - left[1])
      .slice(0, MAX_MODELS)
      .map(([model]) => model);
    const topModelSet = new Set(topModels);
    const buckets = new Map<string, Map<string, number>>();
    for (const row of rows) {
      const model = topModelSet.has(row.model) ? row.model : OTHER_MODEL;
      const byDay = buckets.get(model) ?? new Map<string, number>();
      byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.units);
      buckets.set(model, byDay);
    }
    const modelNames = new Map(
      models.map((model) => [model.metadata.name, modelDisplayName(model)] as const),
    );
    const entries = topModels.map((model, index) => ({
      name: modelNames.get(model) ?? model,
      color: tokens[SERIES_TOKENS[index] ?? "chart5"],
      data: dayKeys.map((day) => buckets.get(model)?.get(day) ?? 0),
    }));
    if (buckets.has(OTHER_MODEL)) {
      entries.push({
        name: "其他",
        color: tokens.mutedForeground,
        data: dayKeys.map((day) => buckets.get(OTHER_MODEL)?.get(day) ?? 0),
      });
    }
    return { days: dayKeys, series: entries };
  }, [models, rows, tokens]);

  const option = useMemo<EChartsOption>(
    () => ({
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
          const rows = items.map(
            (item) =>
              `${item.marker ?? ""}${item.seriesName ?? ""}：${formatTokenCount(
                Number(item.value ?? 0),
              )}`,
          );
          return `${formatTrendDay(String(first.name ?? ""))}<br/>${rows.join("<br/>")}`;
        },
      },
      legend: chartLegend(tokens),
      xAxis: {
        type: "category",
        boundaryGap: true,
        data: days,
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value: string) => formatTrendDay(value),
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
      series: series.map((entry, index) => ({
        type: "bar",
        name: entry.name,
        stack: "units",
        data: entry.data,
        itemStyle: {
          color: entry.color,
          borderRadius: index === series.length - 1 ? [4, 4, 0, 0] : 0,
        },
        barMaxWidth: 32,
      })),
    }),
    [days, series, tokens],
  );

  return (
    <div className="h-[320px] w-full p-4 pt-5">
      <EChart option={option} height="100%" ariaLabel="按模型统计的标准 Token 每日消耗" />
    </div>
  );
}
