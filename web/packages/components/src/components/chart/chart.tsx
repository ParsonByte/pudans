import type { CSSProperties, ReactNode } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";
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
import { cn } from "@pudans/shared";

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

/**
 * Recharts defaults from the legacy Admin/Home chart, re-expressed through the
 * MUI theme: secondary axis text, divider 3-3 dashed grid, default recharts
 * line animation and dots, legend/tooltip typography inherited from the theme.
 */
function useChartStyle() {
  const theme = useTheme();
  const tooltipStyle: CSSProperties = {
    padding: theme.spacing(1.25),
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.paper,
    color: theme.palette.text.primary,
  };
  const tooltipLabelStyle: CSSProperties = {
    color: theme.palette.text.primary,
  };
  return {
    theme,
    tooltipStyle,
    tooltipLabelStyle,
    cursor: { stroke: theme.palette.divider },
  };
}

function ChartEmpty({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
      }}
    >
      <Typography variant="body2" color="text.secondary">
        {children}
      </Typography>
    </Box>
  );
}

function ChartFrame({
  height,
  className,
  children,
}: {
  height: number;
  className?: string;
  children: ReactNode;
}) {
  return (
    <Box
      className={cn(className)}
      sx={{ position: "relative", width: "100%", minHeight: 160, height }}
    >
      {children}
    </Box>
  );
}

/** Line chart wrapper with legacy recharts look. */
export function LineChart({
  data,
  xKey,
  series,
  height = 300,
  className,
  showGrid = true,
  showLegend = true,
  showTooltip = true,
  showDots = true,
  curve = "monotone",
  emptyText = "No data",
}: LineChartProps) {
  const { tooltipStyle, tooltipLabelStyle, cursor } = useChartStyle();

  return (
    <ChartFrame height={height} className={className}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsLineChart data={[...data]}>
            {showGrid ? <CartesianGrid strokeDasharray="3 3" /> : null}
            <XAxis dataKey={xKey} />
            <YAxis allowDecimals={false} />
            {showTooltip ? (
              <Tooltip
                contentStyle={tooltipStyle}
                labelStyle={tooltipLabelStyle}
                cursor={cursor}
              />
            ) : null}
            {showLegend ? <Legend /> : null}
            {series.map((item, index) => (
              <Line
                key={item.key}
                type={curve}
                dataKey={item.key}
                name={item.label ?? item.key}
                stroke={item.color ?? CHART_COLORS[index % CHART_COLORS.length]}
                dot={showDots}
                activeDot
              />
            ))}
          </RechartsLineChart>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  );
}

/** Bar chart wrapper with legacy card-compatible palette. */
export function BarChart({
  data,
  xKey,
  series,
  stacked = false,
  barSize = 18,
  height = 300,
  className,
  showGrid = true,
  showLegend = true,
  showTooltip = true,
  emptyText = "No data",
}: BarChartProps) {
  const { tooltipStyle, tooltipLabelStyle } = useChartStyle();

  return (
    <ChartFrame height={height} className={className}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsBarChart data={[...data]}>
            {showGrid ? <CartesianGrid strokeDasharray="3 3" /> : null}
            <XAxis dataKey={xKey} />
            <YAxis allowDecimals={false} />
            {showTooltip ? (
              <Tooltip contentStyle={tooltipStyle} labelStyle={tooltipLabelStyle} />
            ) : null}
            {showLegend ? <Legend /> : null}
            {series.map((item, index) => (
              <Bar
                key={item.key}
                dataKey={item.key}
                name={item.label ?? item.key}
                stackId={stacked ? "stack" : undefined}
                fill={item.color ?? CHART_COLORS[index % CHART_COLORS.length]}
                maxBarSize={barSize}
              />
            ))}
          </RechartsBarChart>
        </ResponsiveContainer>
      )}
    </ChartFrame>
  );
}

/** Pie/donut chart wrapper with tokenized cells and themed tooltip. */
export function PieChart({
  data,
  nameKey,
  valueKey,
  innerRadius = "52%",
  outerRadius = "78%",
  colors = CHART_COLORS,
  height = 300,
  className,
  showLegend = true,
  showTooltip = true,
  emptyText = "No data",
}: PieChartProps) {
  const { tooltipStyle, tooltipLabelStyle } = useChartStyle();

  return (
    <ChartFrame height={height} className={className}>
      {data.length === 0 ? (
        <ChartEmpty>{emptyText}</ChartEmpty>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <RechartsPieChart>
            {showTooltip ? (
              <Tooltip contentStyle={tooltipStyle} labelStyle={tooltipLabelStyle} />
            ) : null}
            <Pie
              data={[...data]}
              dataKey={valueKey}
              nameKey={nameKey}
              innerRadius={innerRadius}
              outerRadius={outerRadius}
              paddingAngle={2}
              stroke="var(--pd-bg-surface)"
              strokeWidth={2}
            >
              {data.map((entry, index) => (
                <Cell
                  key={String(entry[nameKey] ?? index)}
                  fill={colors[index % colors.length] ?? CHART_COLORS[0]}
                />
              ))}
            </Pie>
            {showLegend ? <Legend /> : null}
          </RechartsPieChart>
        </ResponsiveContainer>
      )}
    </ChartFrame>
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