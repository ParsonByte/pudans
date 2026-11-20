import { Bar, BarChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";

import {
  type ChartConfig,
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart";
import { formatTrendDay } from "@/lib/date";
import { formatNumber } from "@/modules/workspace/ai/providers/lib/model";
import { formatPoints, formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { UsageTrend, UsageTrendMetric } from "../lib/analytics";

export type UsageTrendChartMode = "bar" | "line";

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
  if (metric === "points") {
    return formatPoints(value) ?? "0 点";
  }
  if (metric === "speed") {
    return `${formatNumber(value, 1)} token/秒`;
  }
  return `${formatNumber(value)} 次`;
}

export function RequestTrendChart({ trend, metric, mode, onSelectDay }: RequestTrendChartProps) {
  const config: ChartConfig = Object.fromEntries(
    trend.series.map((series) => [series.key, { label: series.label, color: series.color }]),
  );
  const handleChartClick = (state: { activeLabel?: string | number } | null | undefined) => {
    const label = state?.activeLabel;
    if (onSelectDay && (typeof label === "string" || typeof label === "number")) {
      onSelectDay(String(label));
    }
  };
  const axis = (
    <>
      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" />
      <XAxis
        dataKey="day"
        stroke="var(--muted-foreground)"
        fontSize={12}
        tickLine={false}
        axisLine={false}
        tickFormatter={(value) => formatTrendDay(String(value))}
        interval="preserveStartEnd"
      />
      <YAxis
        stroke="var(--muted-foreground)"
        fontSize={12}
        tickLine={false}
        axisLine={false}
        width={64}
        tickFormatter={(value) => formatTrendMetricValue(metric, Number(value))}
      />
      <ChartTooltip
        content={
          <ChartTooltipContent
            indicator="line"
            labelFormatter={(value) => formatTrendDay(String(value))}
            formatter={(value, name) => (
              <div className="flex w-full items-center justify-between gap-3">
                <span className="text-muted-foreground">
                  {config[String(name)]?.label ?? String(name)}
                </span>
                <span className="font-mono font-medium text-foreground tabular-nums">
                  {formatTrendMetricValue(metric, Number(value))}
                </span>
              </div>
            )}
          />
        }
      />
      <ChartLegend content={<ChartLegendContent />} />
    </>
  );

  return (
    <ChartContainer config={config} className="aspect-auto h-[320px] w-full p-4 pt-5">
      {mode === "bar" ? (
        <BarChart
          data={trend.points}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          onClick={handleChartClick}
        >
          {axis}
          {trend.series.map((series, index) => (
            <Bar
              key={series.key}
              dataKey={series.key}
              stackId={metric === "speed" ? undefined : "trend"}
              fill={`var(--color-${series.key})`}
              radius={metric === "speed" || index === trend.series.length - 1 ? [3, 3, 0, 0] : 0}
            />
          ))}
        </BarChart>
      ) : (
        <LineChart
          data={trend.points}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          onClick={handleChartClick}
        >
          {axis}
          {trend.series.map((series) => (
            <Line
              key={series.key}
              type="monotone"
              dataKey={series.key}
              stroke={`var(--color-${series.key})`}
              strokeWidth={2}
              dot={false}
            />
          ))}
        </LineChart>
      )}
    </ChartContainer>
  );
}
