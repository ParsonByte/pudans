import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

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
  const data = points
    .map((point) => ({ date: point.date ?? "", count: point.count ?? 0 }))
    .filter((point) => point.date)
    .sort((left, right) => left.date.localeCompare(right.date));

  return (
    <div className="w-full p-4 pt-5" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <XAxis
            dataKey="date"
            stroke="var(--muted-foreground)"
            fontSize={12}
            tickLine={false}
            axisLine={false}
            tickFormatter={formatLabel}
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
            labelFormatter={(label) => formatLabel(String(label))}
            formatter={(value) => [Number(value).toLocaleString("zh-CN"), seriesLabel]}
            contentStyle={{
              background: "var(--popover)",
              border: "1px solid var(--border)",
              borderRadius: "0.75rem",
              color: "var(--popover-foreground)",
              fontSize: "0.75rem",
            }}
          />
          <Bar dataKey="count" fill="var(--primary)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
