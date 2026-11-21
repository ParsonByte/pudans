"use client";

import { useEffect, useRef } from "react";

import type { EChartsOption } from "echarts";
import { BarChart, LineChart } from "echarts/charts";
import {
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { useTheme } from "next-themes";

import { cn } from "@/lib/utils";

echarts.use([
  BarChart,
  LineChart,
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

type EChartEvents = Record<string, (params: unknown) => void>;

type EChartProps = {
  option: EChartsOption;
  height?: number | string;
  className?: string;
  ariaLabel?: string;
  events?: EChartEvents;
};

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

export function EChart({ option, height = 300, className, ariaLabel, events }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<typeof echarts.init> | null>(null);
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const chart = echarts.init(container);
    chartRef.current = chart;

    const observer = new ResizeObserver(() => {
      chart.resize();
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, [resolvedTheme]);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) {
      return;
    }
    chart.setOption(
      {
        aria: { enabled: true },
        animation: !prefersReducedMotion(),
        ...option,
      },
      { notMerge: true },
    );
  }, [option, resolvedTheme]);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || !events) {
      return;
    }
    for (const [event, handler] of Object.entries(events)) {
      chart.on(event, handler);
    }
    return () => {
      for (const event of Object.keys(events)) {
        chart.off(event);
      }
    };
  }, [events, resolvedTheme]);

  return (
    <div
      ref={containerRef}
      data-motion="enter"
      role="img"
      aria-label={ariaLabel}
      className={cn("w-full", className)}
      style={{ height }}
    />
  );
}
