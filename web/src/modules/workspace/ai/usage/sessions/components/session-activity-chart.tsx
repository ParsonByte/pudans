import { Bar, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";

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

import type { UsageSessionStatsDay } from "../../lib/api";

const CHART_CONFIG = {
  sessions: { label: "会话数", color: "var(--chart-1)" },
  messages: { label: "消息数", color: "var(--chart-3)" },
} satisfies ChartConfig;

export function SessionActivityChart({ series }: { series: UsageSessionStatsDay[] }) {
  return (
    <ChartContainer config={CHART_CONFIG} className="aspect-auto h-[280px] w-full p-4 pt-5">
      <ComposedChart data={series} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
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
          yAxisId="sessions"
          stroke="var(--muted-foreground)"
          fontSize={12}
          tickLine={false}
          axisLine={false}
          width={44}
          allowDecimals={false}
          tickFormatter={(value) => formatNumber(Number(value))}
        />
        <YAxis
          yAxisId="messages"
          orientation="right"
          stroke="var(--muted-foreground)"
          fontSize={12}
          tickLine={false}
          axisLine={false}
          width={52}
          allowDecimals={false}
          tickFormatter={(value) => formatNumber(Number(value))}
        />
        <ChartTooltip
          content={
            <ChartTooltipContent labelFormatter={(value) => formatTrendDay(String(value))} />
          }
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Bar
          yAxisId="sessions"
          dataKey="sessions"
          fill="var(--color-sessions)"
          radius={[3, 3, 0, 0]}
          barSize={18}
        />
        <Line
          yAxisId="messages"
          type="monotone"
          dataKey="messages"
          stroke="var(--color-messages)"
          strokeWidth={2}
          dot={false}
        />
      </ComposedChart>
    </ChartContainer>
  );
}
