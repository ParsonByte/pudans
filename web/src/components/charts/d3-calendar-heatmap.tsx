"use client";

import { useMemo } from "react";

import { scaleLinear } from "d3";
import { parseISO } from "date-fns";

import type { ChartTokens } from "@/components/charts/chart-theme";
import { useChartTokens } from "@/components/charts/chart-theme";
import { cn } from "@/lib/utils";

type CalendarHeatmapDay = {
  date: string;
  value: number;
};

type CalendarHeatmapTone = "chart1" | "chart2" | "chart3" | "chart4" | "chart5";

type D3CalendarHeatmapProps = {
  from: string;
  days: CalendarHeatmapDay[];
  valueFormatter: (value: number) => string;
  colorTone?: CalendarHeatmapTone;
  ariaLabel?: string;
  className?: string;
};

const WEEK_COUNT = 53;

const CELL_SIZE = 11;

const CELL_GAP = 2;

const STEP = CELL_SIZE + CELL_GAP;

const WEEKDAY_LABELS = ["一", "二", "三", "四", "五", "六", "日"];

const MIN_OPACITY = 0.18;

const MAX_OPACITY = 1;

const TONE_KEYS: Record<CalendarHeatmapTone, keyof ChartTokens> = {
  chart1: "chart1",
  chart2: "chart2",
  chart3: "chart3",
  chart4: "chart4",
  chart5: "chart5",
};

function monthLabels(from: string): { week: number; label: string }[] {
  const start = parseISO(from);
  const labels: { week: number; label: string }[] = [];
  let previous = "";
  for (let week = 0; week < WEEK_COUNT; week += 1) {
    const date = new Date(start);
    date.setDate(date.getDate() + week * 7);
    const month = `${date.getFullYear()}-${date.getMonth()}`;
    if (month !== previous) {
      labels.push({ week, label: `${date.getMonth() + 1} 月` });
      previous = month;
    }
  }
  return labels;
}

export function D3CalendarHeatmap({
  from,
  days,
  valueFormatter,
  colorTone = "chart1",
  ariaLabel,
  className,
}: D3CalendarHeatmapProps) {
  const tokens = useChartTokens();
  const color = tokens[TONE_KEYS[colorTone]];
  const months = useMemo(() => monthLabels(from), [from]);
  const maxValue = useMemo(
    () => days.reduce((maximum, day) => Math.max(maximum, day.value), 0),
    [days],
  );
  const opacity = useMemo(
    () =>
      scaleLinear()
        .domain([0, Math.max(maxValue, 1)])
        .range([MIN_OPACITY, MAX_OPACITY]),
    [maxValue],
  );
  const width = 18 + WEEK_COUNT * STEP;
  const height = 18 + 7 * STEP + 2;

  return (
    <div data-motion="enter" className={cn("overflow-x-auto", className)} aria-label={ariaLabel}>
      <div className="min-w-max">
        <svg
          role="img"
          aria-label={ariaLabel}
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
          shapeRendering="crispEdges"
        >
          {months.map((month) => (
            <text
              key={`${month.week}-${month.label}`}
              x={18 + month.week * STEP}
              y={10}
              fill={tokens.mutedForeground}
              fontSize={10}
            >
              {month.label}
            </text>
          ))}
          {WEEKDAY_LABELS.map((label, index) => (
            <text
              key={label}
              x={0}
              y={18 + index * STEP + CELL_SIZE - 2}
              fill={tokens.mutedForeground}
              fontSize={10}
            >
              {label}
            </text>
          ))}
          {days.map((day, index) => {
            const week = Math.floor(index / 7);
            const row = index % 7;
            if (week >= WEEK_COUNT) {
              return null;
            }
            return (
              <rect
                key={day.date}
                x={18 + week * STEP}
                y={18 + row * STEP}
                width={CELL_SIZE}
                height={CELL_SIZE}
                fill={day.value > 0 ? color : tokens.muted}
                fillOpacity={day.value > 0 ? opacity(day.value) : 1}
              >
                <title>{`${day.date}：${valueFormatter(day.value)}`}</title>
              </rect>
            );
          })}
        </svg>
        <div className="mt-2 flex items-center gap-1 text-[10px] text-muted-foreground">
          <span>少</span>
          {[0.25, 0.5, 0.75, 1].map((level) => (
            <span
              key={level}
              className="size-[10px]"
              style={{ backgroundColor: color, opacity: level }}
            />
          ))}
          <span>多</span>
        </div>
      </div>
    </div>
  );
}
