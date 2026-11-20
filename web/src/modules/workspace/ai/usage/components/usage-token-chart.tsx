import { Area, AreaChart, Bar, BarChart, XAxis, YAxis } from "recharts";

import {
  type ChartConfig,
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart";
import { formatTokenCount } from "@/modules/workspace/ai/routing/lib/model";

import type { UsageBucket, UsageDaily } from "../lib/api";
import { formatUsageBucketLabel } from "../lib/model";

export type UsageTokenChartMode = "area" | "bar";

const CHART_CONFIG = {
  inputTokens: { label: "输入", color: "var(--chart-2)" },
  cacheTokens: { label: "缓存", color: "var(--chart-3)" },
  outputTokens: { label: "输出", color: "var(--chart-1)" },
} satisfies ChartConfig;

type UsageTokenChartProps = {
  series: UsageDaily[];
  mode: UsageTokenChartMode;
  bucket?: UsageBucket;
};

export function UsageTokenChart({ series, mode, bucket = "day" }: UsageTokenChartProps) {
  const axis = (
    <>
      <XAxis
        dataKey="day"
        stroke="var(--muted-foreground)"
        fontSize={12}
        tickLine={false}
        axisLine={false}
        tickFormatter={(value) => formatUsageBucketLabel(String(value), bucket)}
        interval="preserveStartEnd"
      />
      <YAxis
        stroke="var(--muted-foreground)"
        fontSize={12}
        tickLine={false}
        axisLine={false}
        width={56}
        tickFormatter={(value) => formatTokenCount(Number(value))}
      />
      <ChartTooltip
        cursor={false}
        content={
          <ChartTooltipContent
            indicator="line"
            labelFormatter={(value) => formatUsageBucketLabel(String(value), bucket)}
          />
        }
      />
      <ChartLegend content={<ChartLegendContent />} />
    </>
  );

  return (
    <ChartContainer config={CHART_CONFIG} className="aspect-auto h-[300px] w-full p-4 pt-5">
      {mode === "area" ? (
        <AreaChart data={series} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          {axis}
          <Area
            type="monotone"
            dataKey="inputTokens"
            stackId="tokens"
            stroke="var(--color-inputTokens)"
            fill="var(--color-inputTokens)"
            fillOpacity={0.35}
          />
          <Area
            type="monotone"
            dataKey="cacheTokens"
            stackId="tokens"
            stroke="var(--color-cacheTokens)"
            fill="var(--color-cacheTokens)"
            fillOpacity={0.35}
          />
          <Area
            type="monotone"
            dataKey="outputTokens"
            stackId="tokens"
            stroke="var(--color-outputTokens)"
            fill="var(--color-outputTokens)"
            fillOpacity={0.35}
          />
        </AreaChart>
      ) : (
        <BarChart data={series} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          {axis}
          <Bar dataKey="inputTokens" stackId="tokens" fill="var(--color-inputTokens)" />
          <Bar dataKey="cacheTokens" stackId="tokens" fill="var(--color-cacheTokens)" />
          <Bar
            dataKey="outputTokens"
            stackId="tokens"
            fill="var(--color-outputTokens)"
            radius={[4, 4, 0, 0]}
          />
        </BarChart>
      )}
    </ChartContainer>
  );
}
