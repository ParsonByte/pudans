import type { CSSProperties, ReactNode } from "react";
import {
  Bar,
  BarChart as RechartsBarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart as RechartsLineChart,
  Pie,
  PieChart as RechartsPieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { clsx } from "clsx";
import styles from "./chart.module.css";

export const CHART_COLORS = [
  "rgb(var(--pd-chart-1))",
  "rgb(var(--pd-chart-2))",
  "rgb(var(--pd-chart-3))",
  "rgb(var(--pd-chart-4))",
  "rgb(var(--pd-chart-5))",
  "rgb(var(--pd-chart-6))",
] as const;

export type ChartDatum = Record<string, string | number | null | undefined>;

export interface ChartSeries {
  key: string;
  label?: string;
  color?: string;
}

export interface BaseChartProps {
  data: readonly ChartDatum[];
  height?: number;
  className?: string;
  showGrid?: boolean;
  showLegend?: boolean;
  showTooltip?: boolean;
  emptyText?: ReactNode;
}

export interface LineChartProps extends BaseChartProps {
  xKey: string;
  series: readonly ChartSeries[];
  curve?: "linear" | "monotone" | "step";
  showDots?: boolean;
}

export interface BarChartProps extends BaseChartProps {
  xKey: string;
  series: readonly ChartSeries[];
  stacked?: boolean;
  barSize?: number;
}

export interface PieChartProps extends BaseChartProps {
  nameKey: string;
  valueKey: string;
  innerRadius?: number | string;
  outerRadius?: number | string;
  colors?: readonly string[];
}

const tooltipStyle: CSSProperties = {
  padding: "8px 10px",
  border: "1px solid var(--pd-border-default)",
  borderRadius: "var(--pd-radius-md)",
  background: "rgb(var(--pd-bg-elevated))",
  color: "rgb(var(--pd-text-primary))",
  boxShadow: "var(--pd-shadow-md)",
  fontSize: 12,
};

const axisTick = {
  fill: "rgb(var(--pd-text-tertiary))",
  fontSize: 12,
};

const legendStyle: CSSProperties = {
  color: "rgb(var(--pd-text-secondary))",
  fontSize: 12,
};

function ChartEmpty({ children }: { children: ReactNode }) {
  return <div className={styles.empty}>{children}</div>;
}

/** Line chart wrapper with tokenized axes, grid, tooltip and series colors. */
export function LineChart({
  data,
  xKey,
  series,
  height = 280,
  className,
  showGrid = true,
  showLegend = true,
  showTooltip = true,
  showDots = false,
  curve = "monotone",
  emptyText = "No data",
}: LineChartProps) {
  return (
    <div className={clsx(styles.root, className)} style={{ height }}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsLineChart data={[...data]} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            {showGrid ? (
              <CartesianGrid
                stroke="var(--pd-border-subtle)"
                strokeDasharray="3 3"
                vertical={false}
              />
            ) : null}
            <XAxis
              dataKey={xKey}
              tick={axisTick}
              tickLine={false}
              axisLine={{ stroke: "var(--pd-border-subtle)" }}
            />
            <YAxis tick={axisTick} tickLine={false} axisLine={false} width={44} />
            {showTooltip ? (
              <Tooltip
                contentStyle={tooltipStyle}
                labelStyle={{ color: "rgb(var(--pd-text-primary))", fontWeight: 600 }}
                cursor={{ stroke: "var(--pd-border-default)", strokeDasharray: "3 3" }}
              />
            ) : null}
            {showLegend ? <Legend wrapperStyle={legendStyle} /> : null}
            {series.map((item, index) => (
              <Line
                key={item.key}
                type={curve}
                dataKey={item.key}
                name={item.label ?? item.key}
                stroke={item.color ?? CHART_COLORS[index % CHART_COLORS.length]}
                strokeWidth={2}
                dot={showDots ? { r: 3, strokeWidth: 2 } : false}
                activeDot={{ r: 4 }}
              />
            ))}
          </RechartsLineChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

/** Bar chart wrapper with optional stacking and brand palette. */
export function BarChart({
  data,
  xKey,
  series,
  stacked = false,
  barSize = 18,
  height = 280,
  className,
  showGrid = true,
  showLegend = true,
  showTooltip = true,
  emptyText = "No data",
}: BarChartProps) {
  return (
    <div className={clsx(styles.root, className)} style={{ height }}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsBarChart data={[...data]} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            {showGrid ? (
              <CartesianGrid
                stroke="var(--pd-border-subtle)"
                strokeDasharray="3 3"
                vertical={false}
              />
            ) : null}
            <XAxis
              dataKey={xKey}
              tick={axisTick}
              tickLine={false}
              axisLine={{ stroke: "var(--pd-border-subtle)" }}
            />
            <YAxis tick={axisTick} tickLine={false} axisLine={false} width={44} />
            {showTooltip ? (
              <Tooltip
                contentStyle={tooltipStyle}
                labelStyle={{ color: "rgb(var(--pd-text-primary))", fontWeight: 600 }}
                cursor={{ fill: "var(--pd-bg-hover)" }}
              />
            ) : null}
            {showLegend ? <Legend wrapperStyle={legendStyle} /> : null}
            {series.map((item, index) => (
              <Bar
                key={item.key}
                dataKey={item.key}
                name={item.label ?? item.key}
                stackId={stacked ? "stack" : undefined}
                fill={item.color ?? CHART_COLORS[index % CHART_COLORS.length]}
                radius={[6, 6, 0, 0]}
                maxBarSize={barSize}
              />
            ))}
          </RechartsBarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

/** Pie/donut chart wrapper with tokenized cells and tooltip. */
export function PieChart({
  data,
  nameKey,
  valueKey,
  innerRadius = "52%",
  outerRadius = "78%",
  colors = CHART_COLORS,
  height = 280,
  className,
  showLegend = true,
  showTooltip = true,
  emptyText = "No data",
}: PieChartProps) {
  return (
    <div className={clsx(styles.root, className)} style={{ height }}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsPieChart>
            {showTooltip ? (
              <Tooltip
                contentStyle={tooltipStyle}
                labelStyle={{ color: "rgb(var(--pd-text-primary))", fontWeight: 600 }}
              />
            ) : null}
            <Pie
              data={[...data]}
              dataKey={valueKey}
              nameKey={nameKey}
              innerRadius={innerRadius}
              outerRadius={outerRadius}
              paddingAngle={2}
              stroke="rgb(var(--pd-bg-surface))"
              strokeWidth={2}
            >
              {data.map((entry, index) => (
                <Cell
                  key={String(entry[nameKey] ?? index)}
                  fill={colors[index % colors.length] ?? CHART_COLORS[0]}
                />
              ))}
            </Pie>
            {showLegend ? <Legend wrapperStyle={legendStyle} /> : null}
          </RechartsPieChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

export type ChartProps =
  | ({ type: "line" } & LineChartProps)
  | ({ type: "bar" } & BarChartProps)
  | ({ type: "pie" } & PieChartProps);

/** Generic chart switch for data-driven dashboards. */
export function Chart(props: ChartProps) {
  switch (props.type) {
    case "line":
      return <LineChart {...props} />;
    case "bar":
      return <BarChart {...props} />;
    case "pie":
      return <PieChart {...props} />;
    default:
      return null;
  }
}
