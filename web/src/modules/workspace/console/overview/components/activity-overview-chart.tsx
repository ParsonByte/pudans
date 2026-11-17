import type { LoginTrendPoint } from "@pudans-dev/api-client";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import { formatTrendDay } from "../lib/format";

type ActivityOverviewChartProps = {
  points: LoginTrendPoint[];
};

export function ActivityOverviewChart({ points }: ActivityOverviewChartProps) {
  const data = points
    .map((point) => ({ date: point.date ?? "", operations: point.count ?? 0 }))
    .filter((point) => point.date)
    .sort((left, right) => left.date.localeCompare(right.date));

  return (
    <ConsoleSection>
      <ConsoleSectionHeader title="活动概览" description="最近 7 天操作次数" />
      {data.length === 0 ? (
        <ConsoleEmpty>暂无活动数据。</ConsoleEmpty>
      ) : (
        <div className="h-[300px] w-full p-4 pt-5">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <XAxis
                dataKey="date"
                stroke="var(--muted-foreground)"
                fontSize={12}
                tickLine={false}
                axisLine={false}
                tickFormatter={formatTrendDay}
                interval="preserveStartEnd"
              />
              <YAxis
                stroke="var(--muted-foreground)"
                fontSize={12}
                tickLine={false}
                axisLine={false}
                allowDecimals={false}
                width={40}
              />
              <Tooltip
                cursor={{ fill: "var(--muted)" }}
                labelFormatter={(label) => formatTrendDay(String(label))}
                formatter={(value) => [Number(value).toLocaleString("zh-CN"), "操作次数"]}
                contentStyle={{
                  background: "var(--popover)",
                  border: "1px solid var(--border)",
                  borderRadius: "0.75rem",
                  color: "var(--popover-foreground)",
                  fontSize: "0.75rem",
                }}
              />
              <Bar dataKey="operations" fill="var(--primary)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </ConsoleSection>
  );
}
