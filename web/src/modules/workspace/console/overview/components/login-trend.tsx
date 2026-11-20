import type { LoginTrendPoint } from "@pudans-dev/api-client";
import {
  Area,
  AreaChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  type TooltipContentProps,
  XAxis,
  YAxis,
} from "recharts";

import { formatTrendDay } from "@/lib/date";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

const SERIES = [
  { key: "logins", label: "登录", color: "var(--primary)" },
  { key: "operations", label: "操作", color: "var(--muted-foreground)" },
  { key: "riskEvents", label: "风险事件", color: "var(--chart-3)" },
  { key: "webhooks", label: "Webhook", color: "var(--chart-4)" },
] as const;

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
  const loginsByDate = new Map(points.map((point) => [point.date ?? "", point.count ?? 0]));
  const operationsByDate = new Map(
    operationTrend.map((point) => [point.date ?? "", point.count ?? 0]),
  );
  const riskEventsByDate = new Map(
    riskEventTrend.map((point) => [point.date ?? "", point.count ?? 0]),
  );
  const webhooksByDate = new Map(webhookTrend.map((point) => [point.date ?? "", point.count ?? 0]));
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
  const data = dates.map((date) => ({
    date,
    logins: loginsByDate.get(date) ?? 0,
    operations: operationsByDate.get(date) ?? 0,
    riskEvents: riskEventsByDate.get(date) ?? 0,
    webhooks: webhooksByDate.get(date) ?? 0,
  }));

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="审计趋势"
        description="最近 7 天登录、操作、风险事件与 Webhook 次数"
        action={
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            {SERIES.map((series) => (
              <LegendItem key={series.key} label={series.label} color={series.color} />
            ))}
          </div>
        }
      />
      {data.length === 0 ? (
        <ConsoleEmpty>暂无趋势数据。</ConsoleEmpty>
      ) : (
        <div className="h-[300px] w-full p-4 pt-5">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
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
                content={(props) => <TrendTooltip {...props} />}
                cursor={{ stroke: "var(--border)", strokeDasharray: "3 3" }}
              />
              <Area
                type="monotone"
                dataKey="logins"
                stroke="currentColor"
                className="text-primary"
                fill="currentColor"
                fillOpacity={0.15}
                strokeWidth={2}
                activeDot={{ r: 3 }}
              />
              <Area
                type="monotone"
                dataKey="operations"
                stroke="currentColor"
                className="text-muted-foreground"
                fill="currentColor"
                fillOpacity={0.1}
                strokeWidth={2}
                activeDot={{ r: 3 }}
              />
              <Line
                type="monotone"
                dataKey="riskEvents"
                stroke="var(--chart-3)"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 3 }}
              />
              <Line
                type="monotone"
                dataKey="webhooks"
                stroke="var(--chart-4)"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 3 }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </ConsoleSection>
  );
}

function TrendTooltip({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload?.length) {
    return null;
  }

  const values = new Map(payload.map((entry) => [String(entry.dataKey), entry.value]));

  return (
    <div className="min-w-44 border bg-popover p-3 text-popover-foreground shadow-lg">
      <p className="mb-2 text-sm font-semibold">{formatTrendDay(String(label ?? ""))}</p>
      <div className="space-y-1.5">
        {SERIES.map((series) => (
          <div key={series.key} className="flex items-center justify-between gap-6 text-xs">
            <span className="flex items-center gap-2 text-muted-foreground">
              <span className="size-2.5" style={{ backgroundColor: series.color }} />
              {series.label}
            </span>
            <span className="font-medium tabular-nums">
              {Number(values.get(series.key) ?? 0).toLocaleString("zh-CN")}
            </span>
          </div>
        ))}
      </div>
    </div>
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
