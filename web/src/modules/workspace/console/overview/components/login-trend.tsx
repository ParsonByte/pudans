"use client";

import { useMemo } from "react";

import type { LoginTrendPoint } from "@pudans-dev/api-client";
import type { EChartsOption } from "echarts";

import {
  chartAxisLabel,
  chartGrid,
  chartSplitLine,
  chartTooltip,
} from "@/components/charts/chart-options";
import { type ChartTokens, useChartTokens } from "@/components/charts/chart-theme";
import { EChart } from "@/components/charts/echart";
import { formatTrendDay } from "@/lib/date";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

type LoginTrendSeries = {
  key: "logins" | "operations" | "riskEvents" | "webhooks";
  label: string;
  token: keyof ChartTokens;
  variable: string;
  areaOpacity?: number;
};

const SERIES: LoginTrendSeries[] = [
  {
    key: "logins",
    label: "登录",
    token: "primary",
    variable: "var(--primary)",
    areaOpacity: 0.15,
  },
  {
    key: "operations",
    label: "操作",
    token: "mutedForeground",
    variable: "var(--muted-foreground)",
    areaOpacity: 0.1,
  },
  { key: "riskEvents", label: "风险事件", token: "chart3", variable: "var(--chart-3)" },
  { key: "webhooks", label: "Webhook", token: "chart4", variable: "var(--chart-4)" },
];

type LoginTrendProps = {
  points: LoginTrendPoint[];
  operationTrend: LoginTrendPoint[];
  riskEventTrend: LoginTrendPoint[];
  webhookTrend: LoginTrendPoint[];
};

export function LoginTrend({
  points,
  operationTrend,
  riskEventTrend,
  webhookTrend,
}: LoginTrendProps) {
  const tokens = useChartTokens();
  const data = useMemo(() => {
    const loginsByDate = new Map(points.map((point) => [point.date ?? "", point.count ?? 0]));
    const operationsByDate = new Map(
      operationTrend.map((point) => [point.date ?? "", point.count ?? 0]),
    );
    const riskEventsByDate = new Map(
      riskEventTrend.map((point) => [point.date ?? "", point.count ?? 0]),
    );
    const webhooksByDate = new Map(
      webhookTrend.map((point) => [point.date ?? "", point.count ?? 0]),
    );
    const dates = Array.from(
      new Set([
        ...loginsByDate.keys(),
        ...operationsByDate.keys(),
        ...riskEventsByDate.keys(),
        ...webhooksByDate.keys(),
      ]),
    )
      .filter(Boolean)
      .sort();
    return dates.map((date) => ({
      date,
      logins: loginsByDate.get(date) ?? 0,
      operations: operationsByDate.get(date) ?? 0,
      riskEvents: riskEventsByDate.get(date) ?? 0,
      webhooks: webhooksByDate.get(date) ?? 0,
    }));
  }, [operationTrend, points, riskEventTrend, webhookTrend]);
  const option = useMemo<EChartsOption>(() => {
    return {
      grid: chartGrid(),
      tooltip: {
        ...chartTooltip(tokens),
        trigger: "axis",
        axisPointer: {
          type: "line",
          lineStyle: { color: tokens.border, type: "dashed" },
        },
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
          return `<strong>${formatTrendDay(String(first.name ?? ""))}</strong><br/>${rows.join("<br/>")}`;
        },
      },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: data.map((point) => point.date),
        axisLabel: {
          ...chartAxisLabel(tokens),
          formatter: (value) => formatTrendDay(value),
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
      series: SERIES.map((series) => {
        return {
          type: "line" as const,
          name: series.label,
          data: data.map((point) => point[series.key]),
          smooth: true,
          showSymbol: false,
          symbolSize: 6,
          lineStyle: { color: tokens[series.token], width: 2 },
          itemStyle: { color: tokens[series.token] },
          areaStyle:
            series.areaOpacity == null
              ? undefined
              : { color: tokens[series.token], opacity: series.areaOpacity },
        };
      }),
    };
  }, [data, tokens]);

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="审计趋势"
        description="最近 7 天登录、操作、风险事件与 Webhook 次数"
        action={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            {SERIES.map((series) => (
              <LegendItem key={series.key} label={series.label} color={series.variable} />
            ))}
          </div>
        }
      />
      {data.length === 0 ? (
        <ConsoleEmpty>暂无趋势数据。</ConsoleEmpty>
      ) : (
        <div className="h-[300px] w-full p-4 pt-5">
          <EChart option={option} height="100%" ariaLabel="审计趋势" />
        </div>
      )}
    </ConsoleSection>
  );
}

function LegendItem({ label, color }: { label: string; color: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="size-2" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
